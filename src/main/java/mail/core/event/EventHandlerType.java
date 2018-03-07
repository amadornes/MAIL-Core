package mail.core.event;

import mail.api.annotations.ClientOnly;
import mail.api.annotations.ServerOnly;
import mail.api.event.Event;
import mail.api.event.EventPhase;
import mail.api.game.Environment;
import mail.movetolib.util.AnnotationHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

final class EventHandlerType {

    private static final Map<Class<?>, EventHandlerType> STATIC_HANDLERS = new IdentityHashMap<>();
    private static final Map<Class<?>, EventHandlerType> INSTANCED_HANDLERS = new IdentityHashMap<>();

    static EventHandlerType of(Class<?> handler, boolean isStatic) {
        EventHandlerType type = (isStatic ? STATIC_HANDLERS : INSTANCED_HANDLERS).get(handler);
        if (type != null) return type;

        type = new EventHandlerType(handler, isStatic);
        (isStatic ? STATIC_HANDLERS : INSTANCED_HANDLERS).put(handler, type);
        return type;
    }

    private final Class<?> type;
    private final boolean isStatic;
    private final Set<EventHandler> handlers = new HashSet<>();

    private EventHandlerType(Class<?> type, boolean isStatic) {
        this.type = type;
        this.isStatic = isStatic;

        findHandlers();
    }

    private void findHandlers() {
        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) != isStatic) continue;

            Event.Subscribe annotation = AnnotationHelper.getAnnotation(method, Event.Subscribe.class);
            if (annotation == null) continue;

            if (annotation.deferred()) {
                // Check direct annotation instead of inherited
                Event.Subscribe eventAnnotation = method.getAnnotation(Event.Subscribe.class);
                if (eventAnnotation != null && eventAnnotation.deferred()) continue;
            }

            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 0) {
                throw new IllegalStateException("Event subscribers need to at least take in the event as an argument. "
                        + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName());
            }

            Class<?> firstArg = parameters[0];
            if (!Event.class.isAssignableFrom(firstArg)) {
                throw new IllegalStateException("The first argument of an event subscriber must be the event itself. "
                        + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName());
            }
            if (annotation.phase() == EventPhase.CANCELLATION && !Event.Cancelable.class.isAssignableFrom(firstArg)) {
                throw new IllegalStateException("Attempting to register a handler for the cancellation phase of a non-cancellable event. "
                        + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName());
            }

            EventType eventType = EventType.of((Class<? extends Event>) firstArg);
            EventHandler handler = new EventHandler(eventType, method, annotation);
            handlers.add(handler);
        }
    }

    Set<EventHandler> getHandlers() {
        return handlers;
    }

    static final class EventHandler {

        private final EventType eventType;
        private final boolean isStatic;
        private final boolean returnsValue;

        private final EventPhase phase;
        private final boolean receiveCanceled;
        private final Environment.Side side;
        private final Type[] generics;

        private final EventType.Property[] properties;
        private final MethodHandle handle;

        private EventHandler(EventType eventType, Method method, Event.Subscribe annotation) {
            this.eventType = eventType;
            this.isStatic = Modifier.isStatic(method.getModifiers());
            this.returnsValue = method.getReturnType() != Void.TYPE;

            this.phase = annotation.phase();
            this.receiveCanceled = annotation.receiveCanceled();

            ClientOnly clientOnly = AnnotationHelper.getAnnotation(method, ClientOnly.class);
            ServerOnly serverOnly = AnnotationHelper.getAnnotation(method, ServerOnly.class);
            if (clientOnly != null && serverOnly != null) {
                throw new IllegalStateException("A event handler cannot be both client-only and server only. "
                        + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName());
            }
            if (clientOnly != null) {
                this.side = Environment.Side.CLIENT;
            } else if (serverOnly != null) {
                this.side = Environment.Side.SERVER;
            } else {
                this.side = null;
            }

            Type eventParam = method.getGenericParameterTypes()[0];
            if (eventParam instanceof ParameterizedType) {
                this.generics = ((ParameterizedType) eventParam).getActualTypeArguments();
            } else {
                this.generics = new Type[0];
            }

            this.properties = new EventType.Property[method.getParameterCount() - 1];

            Parameter[] parameters = method.getParameters();
            int resultParam = -1;
            for (int i = 1; i < parameters.length; i++) {
                Parameter parameter = parameters[i];

                Event.Result result = AnnotationHelper.getAnnotation(parameter, Event.Result.class);
                if (result != null) {
                    if (resultParam == -1) {
                        resultParam = i;
                    } else {
                        throw new IllegalStateException("Two arguments of an event subscriber are set to receive the "
                                + "previous result. This should not be allowed to happen. "
                                + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName()
                                + " (" + (resultParam + 1) + ", " + (i + 1) + ")");
                    }
                    continue;
                }

                Event.Unpack unpack = AnnotationHelper.getAnnotation(parameter, Event.Unpack.class);
                if (unpack == null) {
                    throw new IllegalStateException("An extra argument of an event subscriber must be annotated with @Event.Unpack or @Event.Result. "
                            + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName() + " (" + (i + 1) + ")");
                }

                EventType.Property property = eventType.getProperty(unpack.value());
                if (property == null) {
                    throw new IllegalStateException("Invalid property name: " + unpack.value() + ". "
                            + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName());
                }

                this.properties[i - 1] = property;
            }

            if (resultParam != -1 && !eventType.hasResult() && phase != EventPhase.CANCELLATION) {
                throw new IllegalStateException("No result value can be retrieved for an event without a result."
                        + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName());
            }

            if (phase == EventPhase.CANCELLATION) {
                if (resultParam == -1 || parameters[resultParam].getType() != Boolean.TYPE || method.getReturnType() != Boolean.TYPE) {
                    throw new IllegalStateException("Event cancellation handlers must take in and output a boolean with the result of the cancellation. "
                            + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName());
                }
            } else {
                Class<?> returnType = method.getReturnType();
                if ((returnType == Void.TYPE) != (resultParam == -1)) {
                    throw new IllegalStateException("Event handlers that deal with results must both take in the previous value and return one."
                            + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName());
                } else if (resultParam != -1 && parameters[resultParam].getType() != returnType) {
                    throw new IllegalStateException("The return type and input of an event handler must match."
                            + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName());
                }
            }

            try {
                this.handle = MethodHandles.publicLookup().unreflect(method);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot access event subscriber. "
                        + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName(), e);
            }
        }

        public EventType getEventType() {
            return eventType;
        }

        public EventPhase getPhase() {
            return phase;
        }

        public Object fire(Object target, Event event, Object prevResult, boolean canceled,
                           Map<EventType.Property, Object> propertyMap) throws Throwable {
            if (canceled && !receiveCanceled) return prevResult;
            if (side != null && event instanceof Event.SideAware && ((Event.SideAware) event).getEventSide() != side)
                return prevResult;
            if (event instanceof Event.Generic) {
                for (int i = 0; i < generics.length; i++) {
                    Class<?> generic = (Class) generics[i];
                    if (generic != null && !((Event.Generic) event).matchesGenericType((Class<? extends Event.Generic>) eventType.getClazz(), i, generic)) {
                        return prevResult;
                    }
                }
            }

            int offset = isStatic ? 1 : 2;
            Object[] arguments = new Object[properties.length + offset];
            if (isStatic) {
                arguments[0] = event;
            } else {
                arguments[0] = target;
                arguments[1] = event;
            }
            for (int i = 0; i < properties.length; i++) {
                EventType.Property property = properties[i];
                if (property != null) {
                    if (property.isMutable()) {
                        arguments[i + offset] = property.get(event);
                    } else {
                        if (propertyMap.containsKey(property)) {
                            arguments[i + offset] = propertyMap.get(property);
                        } else {
                            Object value = property.get(event);
                            arguments[i + offset] = value;
                            propertyMap.put(property, value);
                        }
                    }
                } else {
                    arguments[i + offset] = prevResult;
                }
            }

            Object result = handle.invokeWithArguments(arguments);
            return returnsValue ? result : prevResult;
        }

    }

}

package mail.core.event;

import mail.api.annotations.ClientOnly;
import mail.api.annotations.ServerOnly;
import mail.api.event.Event;
import mail.api.event.EventPhase;
import mail.api.game.Environment;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
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

            Event.Subscribe annotation = method.getAnnotation(Event.Subscribe.class);
            if (annotation == null) continue;

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

            if (!method.getReturnType().equals(Void.TYPE) && !Event.WithResult.class.isAssignableFrom(type)) {
                throw new IllegalStateException("No value can be returned for an event without a result."
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

        private final EventPhase phase;
        private final boolean receiveCanceled;
        private final Environment.Side side;

        private final EventType.Property[] properties;
        private final MethodHandle handle;

        private EventHandler(EventType eventType, Method method, Event.Subscribe annotation) {
            this.eventType = eventType;

            this.phase = annotation.phase();
            this.receiveCanceled = annotation.receiveCanceled();

            ClientOnly clientOnly = method.getAnnotation(ClientOnly.class);
            ServerOnly serverOnly = method.getAnnotation(ServerOnly.class);
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

            this.properties = new EventType.Property[method.getParameterCount() - 1];

            Parameter[] parameters = method.getParameters();
            int resultParam = -1;
            for (int i = 1; i < parameters.length; i++) {
                Parameter parameter = parameters[i];

                Event.Result result = parameter.getAnnotation(Event.Result.class);
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

                Event.Unpack unpack = parameter.getAnnotation(Event.Unpack.class);
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

            if (resultParam != -1 && !eventType.hasResult()) {
                throw new IllegalStateException("No result value can be retrieved for an event without a result."
                        + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName());
            }

            if (phase == EventPhase.CANCELLATION) {
                if (resultParam == -1 || !parameters[resultParam].getType().equals(Boolean.TYPE) || !method.getReturnType().equals(Boolean.TYPE)) {
                    throw new IllegalStateException("Event cancellation handlers must take in and output a boolean with the result of the cancellation. "
                            + "Offender: " + method.getDeclaringClass().getName() + "#" + method.getName());
                }
            } else if (resultParam != -1) {
                if (parameters[resultParam].getType() != method.getReturnType()) {
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

            Object[] arguments = new Object[properties.length + 2];
            arguments[0] = target;
            arguments[1] = event;
            for (int i = 0; i < properties.length; i++) {
                EventType.Property property = properties[i];
                if (property != null) {
                    if (property.isMutable()) {
                        arguments[i + 2] = property.get(event);
                    } else {
                        if (propertyMap.containsKey(property)) {
                            arguments[i + 2] = propertyMap.get(property);
                        } else {
                            Object value = property.get(event);
                            arguments[i + 2] = value;
                            propertyMap.put(property, value);
                        }
                    }
                } else {
                    arguments[i + 2] = prevResult;
                }
            }

            return handle.invokeWithArguments(arguments);
        }

    }

}

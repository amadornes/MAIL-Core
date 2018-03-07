package mail.core.event;

import mail.api.event.Event;
import mail.movetolib.util.AnnotationHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

final class EventType {

    private static final Map<Class<? extends Event>, EventType> EVENT_TYPES = new IdentityHashMap<>();

    static EventType of(Class<? extends Event> event) {
        EventType type = EVENT_TYPES.get(event);
        if (type != null) return type;

        type = new EventType(event);
        EVENT_TYPES.put(event, type);
        return type;
    }

    private final Class<? extends Event> type;
    private final Map<String, Property> properties = new HashMap<>();

    private EventType(Class<? extends Event> type) {
        this.type = type;
        findProperties();
    }

    private void findProperties() {
        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) continue;

            Event.Property annotation = AnnotationHelper.getAnnotation(method, Event.Property.class);
            if (annotation == null) continue;

            if (method.getParameterCount() != 0) {
                throw new IllegalStateException("Methods marked as event properties cannot take in parameters. "
                        + "Offender: " + method.getDeclaringClass() + "#" + method.getName());
            }

            try {
                MethodHandle handle = MethodHandles.publicLookup().unreflect(method);
                properties.put(annotation.value(), new Property(handle, annotation.mutable()));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot access event property. "
                        + "Offender: " + method.getDeclaringClass() + "#" + method.getName(), e);
            }
        }
    }

    public Class<? extends Event> getClazz() {
        return type;
    }

    public Property getProperty(String name) {
        return properties.get(name);
    }

    public boolean hasResult() {
        return Event.WithResult.class.isAssignableFrom(type);
    }

    static final class Property {

        private final MethodHandle handle;
        private final boolean mutable;

        private Property(MethodHandle handle, boolean mutable) {
            this.handle = handle;
            this.mutable = mutable;
        }

        Object get(Event event) throws Throwable {
            return handle.invoke(event);
        }

        boolean isMutable() {
            return mutable;
        }

    }

}

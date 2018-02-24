package mail.core.event;

import mail.api.event.Event;
import mail.api.event.EventBus;
import mail.api.event.EventPhase;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class EventBusImpl implements EventBus {

    private static final EventPhase[] MAIN_PHASES = {EventPhase.PRE, EventPhase.DEFAULT, EventPhase.POST};

    private final Map<EventType, EventDispatcher> dispatchers = new IdentityHashMap<>();
    private final Map<EventType, Set<EventDispatcher>> allDispatchers = new IdentityHashMap<>();

    private Set<EventDispatcher> computeDispatchers(EventType type) {
        Set<EventDispatcher> set = allDispatchers.get(type);
        if (set != null) return set;

        EventDispatcher dispatcher = new EventDispatcher();

        dispatchers.put(type, dispatcher);
        allDispatchers.put(type, set = new HashSet<>());

        // Send events to supertypes
        Queue<Class<? extends Event>> queue = new ArrayDeque<>();
        queue.add(type.getClazz());
        while (!queue.isEmpty()) {
            Class<? extends Event> current = queue.poll();
            EventType currentEventType = EventType.of(current);

            EventDispatcher currentDispatcher = dispatchers.get(currentEventType);
            if (currentDispatcher != null) {
                set.add(currentDispatcher);
            }

            for (Class<?> itf : current.getInterfaces()) {
                if (Event.class.isAssignableFrom(itf)) {
                    queue.add((Class<? extends Event>) itf);
                }
            }

            Class<?> superclass = current.getSuperclass();
            if (superclass != null && Event.class.isAssignableFrom(superclass)) {
                queue.add((Class<? extends Event>) superclass);
            }
        }

        // Receive events from subtypes
        for (EventType currentType : allDispatchers.keySet()) {
            if (currentType != type && type.getClazz().isAssignableFrom(currentType.getClazz())) {
                allDispatchers.get(currentType).add(dispatcher);
            }
        }

        return set;
    }

    @Override
    public void register(Object listener) {
        EventHandlerType handlerType;
        if (listener instanceof Class) {
            handlerType = EventHandlerType.of((Class<?>) listener, true);
        } else {
            handlerType = EventHandlerType.of(listener.getClass(), false);
        }

        Set<EventHandlerType.EventHandler> handlers = handlerType.getHandlers();
        for (EventHandlerType.EventHandler handler : handlers) {
            computeDispatchers(handler.getEventType());
            EventDispatcher dispatcher = dispatchers.get(handler.getEventType());
            dispatcher.addHandler(handler, listener instanceof Class ? null : listener);
        }
    }

    @Override
    public void unregister(Object listener) throws IllegalStateException {
        EventHandlerType handlerType;
        if (listener instanceof Class) {
            handlerType = EventHandlerType.of((Class<?>) listener, true);
        } else {
            handlerType = EventHandlerType.of(listener.getClass(), false);
        }

        Set<EventHandlerType.EventHandler> handlers = handlerType.getHandlers();
        for (EventHandlerType.EventHandler handler : handlers) {
            computeDispatchers(handler.getEventType());
            EventDispatcher dispatcher = dispatchers.get(handler.getEventType());
            dispatcher.removeHandler(handler, listener instanceof Class ? null : listener);
        }
    }

    private void post(Event event, EventContext context) {
        Set<EventDispatcher> dispatchers = computeDispatchers(EventType.of(event.getClass()));
        try {
            for (EventPhase phase : MAIN_PHASES) {
                context.phase = phase;
                for (EventDispatcher dispatcher : dispatchers) {
                    dispatcher.fire(event, context);
                }
            }
        } catch (Throwable t) {
            throw new IllegalStateException("There was an exception trying to fire an event.", t);
        }
    }

    @Override
    public <T extends Event> T post(T event) {
        EventContext context = new EventContext();
        if (event instanceof Event.WithResult<?>) {
            context.result = ((Event.WithResult) event).getDefaultResult();
        }

        post(event, context);

        return event;
    }

    @Override
    public <T> T post(Event.WithResult<T> event) {
        EventContext context = new EventContext();
        context.result = event.getDefaultResult();

        post(event, context);

        return (T) context.result;
    }

    @Override
    public PostedEvent postManually(Event event) {
        return new PostedEventImpl(event);
    }

    @Override
    public <T> PostedEvent.WithResult<T> postManually(Event.WithResult<T> event) {
        return new PostedEventWithResult<>(event);
    }

    private class PostedEventImpl implements PostedEvent {

        private final Event event;
        protected final EventContext context = new EventContext();
        private final Set<EventDispatcher> dispatchers;

        private PostedEventImpl(Event event) {
            this.event = event;
            this.dispatchers = computeDispatchers(EventType.of(event.getClass()));

            if (event instanceof Event.Cancelable) {
                fire(EventPhase.CANCELLATION);
            }
        }

        @Override
        public boolean wasCancelled() {
            return context.canceled;
        }

        @Override
        public boolean hasListeners() {
            return true; // TODO: Actually check dispatchers for listeners
        }

        @Override
        public void firePre() {
            fire(EventPhase.PRE);
        }

        @Override
        public void fireDefault() {
            fire(EventPhase.DEFAULT);
        }

        @Override
        public void firePost() {
            fire(EventPhase.POST);
        }

        private void fire(EventPhase phase) {
            context.phase = phase;
            try {
                for (EventDispatcher dispatcher : dispatchers) {
                    dispatcher.fire(event, context);
                }
            } catch (Throwable t) {
                throw new IllegalStateException("There was an exception trying to fire an event.", t);
            }
        }

    }

    private final class PostedEventWithResult<T> extends PostedEventImpl implements PostedEvent.WithResult<T> {

        private PostedEventWithResult(Event.WithResult<T> event) {
            super(event);
        }

        @Override
        public T getResult() {
            return (T) context.result;
        }

    }

    private final class EventContext {

        private EventPhase phase;
        private boolean canceled = false;
        private Object result;
        private Map<EventType.Property, Object> propertyMap = new IdentityHashMap<>(); // TODO: Check memory implications

    }

    private static final class EventDispatcher {

        private final Map<EventHandlerType.EventHandler, Set<Object>> handlers = new IdentityHashMap<>();

        private void addHandler(EventHandlerType.EventHandler handler, Object target) {
            Set<Object> targets = handlers.computeIfAbsent(handler, k -> new HashSet<>());
            targets.add(target);
        }

        private void removeHandler(EventHandlerType.EventHandler handler, Object target) {
            Set<Object> targets = handlers.get(handler);
            if (targets == null) return;

            targets.remove(target);
            if (targets.isEmpty()) {
                handlers.remove(handler);
            }
        }

        private void fire(Event event, EventBusImpl.EventContext context) throws Throwable {
            for (Map.Entry<EventHandlerType.EventHandler, Set<Object>> entry : handlers.entrySet()) {
                if(entry.getKey().getPhase() != context.phase) continue;
                for (Object o : entry.getValue()) {
                    Object prevResult = context.phase == EventPhase.CANCELLATION ? context.canceled : context.result;
                    boolean canceled = context.phase != EventPhase.CANCELLATION && context.canceled;
                    Object result = entry.getKey().fire(o, event, prevResult, canceled, context.propertyMap);
                    if (context.phase == EventPhase.CANCELLATION) {
                        context.canceled = (boolean) result;
                    } else {
                        context.result = result;
                    }
                }
            }
        }

    }

}

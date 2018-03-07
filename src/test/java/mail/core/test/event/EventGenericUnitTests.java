package mail.core.test.event;

import mail.api.event.Event;
import mail.api.event.EventBus;
import mail.core.event.EventBusImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class EventGenericUnitTests {

    private final EventBus bus = new EventBusImpl();

    public EventGenericUnitTests() {
        bus.register(Listener.class);
    }

    @Test
    public void noGenerics() {
        Listener.received = 0;
        bus.post(new NoGenericsEvent());
        Assertions.assertEquals(1, Listener.received, "Did not receive event!");
    }

    @Test
    public void oneGeneric() {
        Listener.received = 0;
        bus.post(new OneGenericEvent<>(List.class));
        Assertions.assertEquals(0, Listener.received, "Received an event we should not have got!");

        Listener.received = 0;
        bus.post(new OneGenericEvent<>(String.class));
        Assertions.assertEquals(1, Listener.received, "Received incorrect amount of events!");

        Listener.received = 0;
        bus.post(new OneGenericEvent<>(Integer.class));
        Assertions.assertEquals(1, Listener.received, "Received incorrect amount of events!");
    }

    @Test
    public void twoGenerics() {
        Listener.received = 0;
        bus.post(new TwoGenericsEvent<>(String.class, List.class));
        Assertions.assertEquals(0, Listener.received, "Received an event we should not have got!");

        Listener.received = 0;
        bus.post(new TwoGenericsEvent<>(String.class, Integer.class));
        Assertions.assertEquals(1, Listener.received, "Received incorrect amount of events!");

        Listener.received = 0;
        bus.post(new TwoGenericsEvent<>(Integer.class, String.class));
        Assertions.assertEquals(1, Listener.received, "Received incorrect amount of events!");
    }

    @Test
    public void genericReplacement() {
        Listener.received = 0;
        bus.post(new ReplacedGenericEvent<>(List.class));
        Assertions.assertEquals(1, Listener.received, "Received incorrect amount of events!");

        Listener.received = 0;
        bus.post(new ReplacedGenericEvent<>(Integer.class));
        Assertions.assertEquals(2, Listener.received, "Received incorrect amount of events!");
    }

    private static class NoGenericsEvent implements Event.Generic {

        @Override
        public boolean matchesGenericType(Class<? extends Generic> eventType, int index, Class<?> type) {
            return false;
        }

    }

    private static class OneGenericEvent<T> implements Event.Generic {

        private final Class<?> generic;

        public OneGenericEvent(Class<?> generic) {
            this.generic = generic;
        }

        @Override
        public boolean matchesGenericType(Class<? extends Generic> eventType, int index, Class<?> type) {
            return eventType == OneGenericEvent.class && index == 0 && type == generic;
        }

    }

    private static class TwoGenericsEvent<T, V> implements Event.Generic {

        private final Class<?> generic1;
        private final Class<?> generic2;

        private TwoGenericsEvent(Class<?> generic1, Class<?> generic2) {
            this.generic1 = generic1;
            this.generic2 = generic2;
        }

        @Override
        public boolean matchesGenericType(Class<? extends Generic> eventType, int index, Class<?> type) {
            return eventType == TwoGenericsEvent.class && ((index == 0 && type == generic1) || (index == 1 && type == generic2));
        }

    }

    private static class ReplacedGenericEvent<T> extends OneGenericEvent<String> {

        private final Class<?> generic;

        public ReplacedGenericEvent(Class<?> generic) {
            super(String.class);
            this.generic = generic;
        }

        @Override
        public boolean matchesGenericType(Class<? extends Generic> eventType, int index, Class<?> type) {
            if (eventType == ReplacedGenericEvent.class) return index == 0 && type == generic;
            return super.matchesGenericType(eventType, index, type);
        }

    }

    public static class Listener {

        private static int received = 0;

        @Event.Subscribe
        public static void onNoEvent(NoGenericsEvent event) {
            received++;
        }

        @Event.Subscribe
        public static void onOneEvent1(OneGenericEvent<String> event) {
            received++;
        }

        @Event.Subscribe
        public static void onOneEvent2(OneGenericEvent<Integer> event) {
            received++;
        }

        @Event.Subscribe
        public static void onTwoEvent1(TwoGenericsEvent<String, Integer> event) {
            received++;
        }

        @Event.Subscribe
        public static void onTwoEvent2(TwoGenericsEvent<Integer, String> event) {
            received++;
        }

        @Event.Subscribe
        public static void onReplacedEvent(ReplacedGenericEvent<Integer> event) {
            received++;
        }

    }

}

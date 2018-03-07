package mail.core.test.event;

import mail.api.event.Event;
import mail.api.event.EventBus;
import mail.core.event.EventBusImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EventUnpackUnitTests {

    @Test
    public void registerValidListener() {
        EventBus eventBus = new EventBusImpl();
        eventBus.register(ValidListener.class);
    }

    @Test
    public void registerInvalidListener() {
        EventBus eventBus = new EventBusImpl();
        Assertions.assertThrows(IllegalStateException.class, () -> eventBus.register(InvalidListener.class));
    }

    @Test
    public void unpackImmutable() {
        EventBus eventBus = new EventBusImpl();
        eventBus.register(ImmutableUnpackListener.class);
        eventBus.post(new TestEvent());
    }

    @Test
    public void unpackMutable() {
        EventBus eventBus = new EventBusImpl();
        eventBus.register(MutableUnpackListener.class);
        eventBus.post(new TestEvent());
    }

    public static class TestEvent implements Event {

        private static final int IMMUTABLE_VALUE = -1;
        private boolean retrieved = false;

        @Property("immutable")
        public int getImmutable() {
            Assertions.assertFalse(retrieved, "Retrieved value of immutable property multiple times!");
            retrieved = true;
            return IMMUTABLE_VALUE;
        }

        @Property(value = "mutable", mutable = true)
        public int getMutable() {
            int result = retrieved ? 1 : 0;
            retrieved = true;
            return result;
        }

    }

    public static class ValidListener {

        @Event.Subscribe
        public static void onEvent(TestEvent event) {
            // NO-OP, this is just to test registration exceptions
        }

    }

    public static class InvalidListener {

        @Event.Subscribe
        public static void onEvent(TestEvent event, @Event.Unpack("nonexistent") Object nonexistent) {
            // NO-OP, this is just to test registration exceptions
        }

    }

    public static class ImmutableUnpackListener {

        @Event.Subscribe
        public static void onEvent1(TestEvent event, @Event.Unpack("immutable") int value) {
            Assertions.assertEquals(TestEvent.IMMUTABLE_VALUE, value);
        }

        @Event.Subscribe
        public static void onEvent2(TestEvent event, @Event.Unpack("immutable") int value) {
            Assertions.assertEquals(TestEvent.IMMUTABLE_VALUE, value);
        }

    }

    public static class MutableUnpackListener {

        private static boolean first = true;

        @Event.Subscribe
        public static void onEvent1(TestEvent event, @Event.Unpack("mutable") int value) {
            Assertions.assertEquals(first ? 0 : 1, value);
            first = false;
        }

        @Event.Subscribe
        public static void onEvent2(TestEvent event, @Event.Unpack("mutable") int value) {
            Assertions.assertEquals(first ? 0 : 1, value);
            first = false;
        }

    }

}

package mail.core.test.event;

import mail.api.event.Event;
import mail.api.event.EventBus;
import mail.core.event.EventBusImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EventDeferringUnitTests {

    @Test
    public void testNonDeferred() {
        EventBus bus = new EventBusImpl();
        bus.register(NonDeferredListener.class);
        bus.post(new TestEvent());
        Assertions.assertTrue(NonDeferredListener.received, "Event was not received!");
    }

    @Test
    public void testDeferredUnhandled() {
        EventBus bus = new EventBusImpl();
        bus.register(DeferredUnhandledListener.class);
        bus.post(new TestEvent());
    }

    @Test
    public void testDeferredHandled() {
        EventBus bus = new EventBusImpl();
        bus.register(new DeferredHandledListener.Child());
        bus.post(new TestEvent());
        Assertions.assertTrue(DeferredHandledListener.Child.received, "Event was not received!");
    }

    private static class TestEvent implements Event {
    }

    public static class NonDeferredListener {

        private static boolean received = false;

        @Event.Subscribe
        public static void onEvent(TestEvent event) {
            received = true;
        }

    }

    public static class DeferredUnhandledListener {

        @Event.Subscribe(deferred = true)
        public static void onEvent(TestEvent event) {
            Assertions.assertTrue(false, "Received deferred event!");
        }

    }

    public static class DeferredHandledListener {

        @Event.Subscribe(deferred = true)
        public void onEvent(TestEvent event) {
            Assertions.assertTrue(false, "Received deferred event!");
        }

        public static class Child extends DeferredHandledListener {

            private static boolean received = false;

            @Override
            public void onEvent(TestEvent event) {
                received = true;
            }
        }

    }

}

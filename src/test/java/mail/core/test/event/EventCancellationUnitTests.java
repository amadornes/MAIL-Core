package mail.core.test.event;

import mail.api.event.Event;
import mail.api.event.EventBus;
import mail.api.event.EventPhase;
import mail.core.event.EventBusImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EventCancellationUnitTests {

    @Test
    public void registerValidListeners() {
        EventBus eventBus = new EventBusImpl();
        eventBus.register(ValidListener.class);
    }

    @Test
    public void registerInvalidListeners() {
        EventBus eventBus = new EventBusImpl();
        Assertions.assertThrows(IllegalStateException.class, () -> eventBus.register(NotCancellableListener.class));
        Assertions.assertThrows(IllegalStateException.class, () -> eventBus.register(IgnoreCancellationListener.class));
        Assertions.assertThrows(IllegalStateException.class, () -> eventBus.register(OnlyReturnListener.class));
        Assertions.assertThrows(IllegalStateException.class, () -> eventBus.register(OnlyParameterListener.class));
    }

    @Test
    public void cancelEvent() {
        EventBus eventBus = new EventBusImpl();
        eventBus.register(CancellingListener.class);
        eventBus.post(new TestCancelableEvent());
    }

    @Test
    public void noCancelEvent() {
        EventBus eventBus = new EventBusImpl();
        eventBus.register(NonCancellingListener.class);
        eventBus.post(new TestCancelableEvent());
        Assertions.assertTrue(NonCancellingListener.fired, "The event was not fired!");
    }

    @Test
    public void cancelUncancelEvent() {
        EventBus eventBus = new EventBusImpl();
        eventBus.register(CancellingUncancellingListener.class);
        eventBus.post(new TestCancelableEvent());
        Assertions.assertTrue(CancellingUncancellingListener.fired, "The event was not fired!");
    }

    @Test
    public void cancelListenEvent() {
        EventBus eventBus = new EventBusImpl();
        eventBus.register(CancelledListener.class);
        eventBus.post(new TestCancelableEvent());
        Assertions.assertTrue(CancelledListener.fired, "The event was not fired!");
    }

    private class TestEvent implements Event {

    }

    private class TestCancelableEvent implements Event.Cancelable {

    }

    public static class ValidListener {

        @Event.Subscribe(phase = EventPhase.CANCELLATION)
        public static boolean onTestEventCancellation(TestCancelableEvent event, @Event.Result boolean prevResult) {
            return prevResult;
        }

    }

    public static class NotCancellableListener {

        @Event.Subscribe(phase = EventPhase.CANCELLATION)
        public static boolean onTestEventCancellation(TestEvent event, @Event.Result boolean prevResult) {
            return prevResult;
        }

    }

    public static class IgnoreCancellationListener {

        @Event.Subscribe(phase = EventPhase.CANCELLATION)
        public static void onTestEventCancellation(TestCancelableEvent event) {
        }

    }

    public static class OnlyReturnListener {

        @Event.Subscribe(phase = EventPhase.CANCELLATION)
        public static boolean onTestEventCancellation(TestCancelableEvent event) {
            return true;
        }

    }

    public static class OnlyParameterListener {

        @Event.Subscribe(phase = EventPhase.CANCELLATION)
        public static void onTestEventCancellation(TestCancelableEvent event, @Event.Result boolean prevResult) {
        }

    }

    public static class CancellingListener {

        @Event.Subscribe(phase = EventPhase.CANCELLATION)
        public static boolean onTestEventCancellation(TestCancelableEvent event, @Event.Result boolean prevResult) {
            return true; // Cancel
        }

        @Event.Subscribe
        public static void onTestEvent(TestCancelableEvent event) {
            Assertions.assertTrue(false, "The cancellation phase was not fired!");
        }

    }

    public static class NonCancellingListener {

        private static boolean fired = false;

        @Event.Subscribe(phase = EventPhase.CANCELLATION)
        public static boolean onTestEventCancellation(TestCancelableEvent event, @Event.Result boolean prevResult) {
            return false; // Don't cancel
        }

        @Event.Subscribe
        public static void onTestEvent(TestCancelableEvent event) {
            fired = true;
        }

    }

    public static class CancellingUncancellingListener {

        private static boolean fired = false;

        @Event.Subscribe(phase = EventPhase.CANCELLATION)
        public static boolean onTestEventCancellation1(TestCancelableEvent event, @Event.Result boolean prevResult) {
            return !prevResult;
        }

        @Event.Subscribe(phase = EventPhase.CANCELLATION)
        public static boolean onTestEventCancellation2(TestCancelableEvent event, @Event.Result boolean prevResult) {
            return !prevResult;
        }

        @Event.Subscribe
        public static void onTestEvent(TestCancelableEvent event) {
            fired = true;
        }

    }

    public static class CancelledListener {

        private static boolean fired = false;

        @Event.Subscribe(phase = EventPhase.CANCELLATION)
        public static boolean onTestEventCancellation1(TestCancelableEvent event, @Event.Result boolean prevResult) {
            return true;
        }

        @Event.Subscribe(receiveCanceled = true)
        public static void onTestEvent(TestCancelableEvent event) {
            fired = true;
        }

    }

}

package mail.core.test.event;

import mail.api.event.Event;
import mail.api.event.EventBus;
import mail.core.event.EventBusImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EventResultUnitTests {

    @Test
    public void registerValidListeners() {
        EventBus eventBus = new EventBusImpl();
        eventBus.register(NoResultListener.class);
        eventBus.register(BoxedResultListener.class);
        eventBus.register(PrimitiveResultListener.class);
    }

    @Test
    public void registerInvalidListeners() {
        EventBus eventBus = new EventBusImpl();
        Assertions.assertThrows(IllegalStateException.class, () -> eventBus.register(OnlyReturnListener.class));
        Assertions.assertThrows(IllegalStateException.class, () -> eventBus.register(OnlyParameterListener.class));
    }

    @Test
    public void testEventOutput() {
        EventBus eventBus = new EventBusImpl();
        eventBus.register(NoResultListener.class);
        eventBus.register(BoxedResultListener.class);
        eventBus.register(PrimitiveResultListener.class);

        boolean result = eventBus.post(new TestEvent());
        Assertions.assertFalse(result, "Expected false!");
    }

    private static class TestEvent implements Event.WithResult<Boolean> {

        @Override
        public Boolean getDefaultResult() {
            return false;
        }

    }

    public static class NoResultListener {

        @Event.Subscribe
        public static void onEvent(TestEvent event) {
            // NO-OP, this is just to test registration exceptions
        }

    }

    public static class BoxedResultListener {

        @Event.Subscribe
        public static Boolean onEvent(TestEvent event, @Event.Result Boolean prevResult) {
            return !prevResult;
        }

    }

    public static class PrimitiveResultListener {

        @Event.Subscribe
        public static boolean onEvent(TestEvent event, @Event.Result boolean prevResult) {
            return !prevResult;
        }

    }

    public static class OnlyReturnListener {

        @Event.Subscribe
        public static boolean onEvent(TestEvent event) {
            return true;
        }

    }

    public static class OnlyParameterListener {

        @Event.Subscribe
        public static void onEvent(TestEvent event, @Event.Result boolean prevResult) {
        }

    }

}

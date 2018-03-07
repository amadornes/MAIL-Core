package mail.core.test.event;

import mail.api.event.Event;
import mail.api.event.EventBus;
import mail.api.event.EventPhase;
import mail.core.event.EventBusImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EventBusPostUnitTests {

    private final EventBus eventBus = new EventBusImpl();

    public EventBusPostUnitTests() {
        eventBus.register(Listener.class);
    }

    @Test
    public void post(){
        // Expect all 3 phases
        Listener.expectedEvent = 0b111;
        eventBus.post(new TestEvent());
    }

    @Test
    public void postWithResult(){
        // Expect all 3 phases
        Listener.expectedEvent = 0b111;
        Boolean result = eventBus.post(new TestEventWithResult());
        Assertions.assertTrue(result, "Expected true!");
    }

    @Test
    public void postManually(){
        Listener.expectedEvent = 0b000;
        EventBus.PostedEvent event = eventBus.postManually(new TestEvent());

        Listener.expectedEvent = 0b001;
        event.firePre();

        Listener.expectedEvent = 0b010;
        event.fireDefault();

        Listener.expectedEvent = 0b100;
        event.firePost();
    }

    @Test
    public void postManuallyWithResult(){
        Listener.expectedEvent = 0b000;
        EventBus.PostedEvent.WithResult<Boolean> event = eventBus.postManually(new TestEventWithResult());

        Listener.expectedEvent = 0b001;
        event.firePre();

        Listener.expectedEvent = 0b010;
        event.fireDefault();

        Listener.expectedEvent = 0b100;
        event.firePost();

        Assertions.assertTrue(event.getResult(), "Expected true!");
    }

    private static class TestEvent implements Event {
    }

    private static class TestEventWithResult implements Event.WithResult<Boolean> {

        @Override
        public Boolean getDefaultResult() {
            return false;
        }

    }

    public static class Listener {

        private static int expectedEvent = 0;

        @Event.Subscribe(phase = EventPhase.PRE)
        public static void onEventPre(TestEvent event) {
            Assertions.assertNotEquals(0, expectedEvent & 0b001, "Did not expect event on PRE phase!");
            System.out.println("Got PRE phase!");
        }

        @Event.Subscribe(phase = EventPhase.DEFAULT)
        public static void onEvent(TestEvent event) {
            Assertions.assertNotEquals(0, expectedEvent & 0b010, "Did not expect event on DEFAULT phase!");
            System.out.println("Got DEFAULT phase!");
        }

        @Event.Subscribe(phase = EventPhase.POST)
        public static void onEventPost(TestEvent event) {
            Assertions.assertNotEquals(0, expectedEvent & 0b100, "Did not expect event on POST phase!");
            System.out.println("Got POST phase!");
        }

        @Event.Subscribe(phase = EventPhase.PRE)
        public static Boolean onEventWithResultPre(TestEventWithResult event, @Event.Result Boolean prevResult) {
            Assertions.assertNotEquals(0, expectedEvent & 0b001, "Did not expect event on PRE phase!");
            Assertions.assertFalse(prevResult, "Expected false!");

            System.out.println("Got PRE phase!");

            return true;
        }

        @Event.Subscribe(phase = EventPhase.DEFAULT)
        public static Boolean onEventWithResult(TestEventWithResult event, @Event.Result Boolean prevResult) {
            Assertions.assertNotEquals(0, expectedEvent & 0b010, "Did not expect event on DEFAULT phase!");
            Assertions.assertTrue(prevResult, "Expected true!");

            System.out.println("Got DEFAULT phase!");

            return false;
        }

        @Event.Subscribe(phase = EventPhase.POST)
        public static Boolean onEventWithResultPost(TestEventWithResult event, @Event.Result Boolean prevResult) {
            Assertions.assertNotEquals(0, expectedEvent & 0b100, "Did not expect event on POST phase!");
            Assertions.assertFalse(prevResult, "Expected false!");

            System.out.println("Got POST phase!");

            return true;
        }

    }

}

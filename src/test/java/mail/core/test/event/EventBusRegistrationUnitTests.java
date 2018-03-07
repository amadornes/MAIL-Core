package mail.core.test.event;

import mail.api.event.Event;
import mail.api.event.EventBus;
import mail.core.event.EventBusImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EventBusRegistrationUnitTests {

    @Test
    public void register(){
        EventBus eventBus = new EventBusImpl();
        Listener listener = new Listener();

        listener.expectsEvent = true;
        eventBus.register(listener);
        eventBus.post(new TestEvent());
    }

    @Test
    public void registerStatic(){
        EventBus eventBus = new EventBusImpl();

        StaticListener.expectsEvent = true;
        eventBus.register(StaticListener.class);
        eventBus.post(new TestEvent());
    }

    @Test
    public void registerUnregister(){
        EventBus eventBus = new EventBusImpl();
        Listener listener = new Listener();

        listener.expectsEvent = true;
        eventBus.register(listener);
        eventBus.post(new TestEvent());

        listener.expectsEvent = false;
        eventBus.unregister(listener);
        eventBus.post(new TestEvent());
    }

    @Test
    public void registerUnregisterStatic(){
        EventBus eventBus = new EventBusImpl();

        StaticListener.expectsEvent = true;
        eventBus.register(StaticListener.class);
        eventBus.post(new TestEvent());

        StaticListener.expectsEvent = false;
        eventBus.unregister(StaticListener.class);
        eventBus.post(new TestEvent());
    }

    private static class TestEvent implements Event {
    }

    public static class Listener {

        private boolean expectsEvent;

        @Event.Subscribe
        public void onTestEvent(TestEvent event){
            Assertions.assertTrue(expectsEvent, "Unexpected event!");
        }

    }

    public static class StaticListener {

        private static boolean expectsEvent;

        @Event.Subscribe
        public static void onTestEvent(TestEvent event){
            Assertions.assertTrue(expectsEvent, "Unexpected event!");
        }

    }

}

package net.reini.rabbitmq.cdi;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

@RunWith(MockitoJUnitRunner.class)
public class GenericPublisherTest {
  @Mock
  private ConnectionProducer connectionProducer;
  @Mock
  private Connection connection;
  @Mock
  private Channel channel;

  private GenericPublisher publisher;
  private TestEvent event;

  @Before
  public void setUp() throws Exception {
    publisher = new GenericPublisher(connectionProducer);
    event = new TestEvent();
    event.id = "theId";
    event.booleanValue = true;
  }

  @Test
  public void test() throws Exception {
    BasicProperties props = new BasicProperties();
    PublisherConfiguration publisherConfiguration =
        new PublisherConfiguration("exchange", "routingKey", false, props);
    ArgumentCaptor<BasicProperties> propsCaptor = ArgumentCaptor.forClass(BasicProperties.class);

    when(connectionProducer.newConnection()).thenReturn(connection);
    when(connection.createChannel()).thenReturn(channel);

    publisher.publish(event, publisherConfiguration);

    verify(channel).basicPublish(eq("exchange"), eq("routingKey"), propsCaptor.capture(),
        eq("{\"id\":\"theId\",\"booleanValue\":true}".getBytes()));
    assertEquals("application/json", propsCaptor.getValue().getContentType());
  }
}

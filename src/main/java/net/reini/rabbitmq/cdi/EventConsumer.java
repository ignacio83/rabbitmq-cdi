package net.reini.rabbitmq.cdi;

import java.io.IOException;

import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

public class EventConsumer implements Consumer {
  private static final Logger LOGGER = LoggerFactory.getLogger(EventConsumer.class);

  private final boolean autoAck;
  private final Decoder<?> decoder;
  private final Event<Object> eventControl;
  private final Instance<Object> eventPool;

  private Channel channel;

  EventConsumer(Decoder<?> decoder, boolean autoAck, Event<Object> eventControl,
      Instance<Object> eventPool) {
    this.decoder = decoder;
    this.autoAck = autoAck;
    this.eventControl = eventControl;
    this.eventPool = eventPool;
  }

  Channel getChannel() {
    return channel;
  }

  void setChannel(Channel channel) {
    this.channel = channel;
  }

  /**
   * Builds a CDI event from a message. The CDI event instance is retrieved from the injection
   * container.
   *
   * @param messageBody The message
   * @return The CDI event
   */
  Object buildEvent(byte[] messageBody) {
    Object event;
    try {
      event = decoder.decode(messageBody);
    } catch (DecodeException e) {
      LOGGER.error("Unable to read decode event from message: ".concat(new String(messageBody)), e);
      event = eventPool.get();
    }
    return event;
  }

  @Override
  public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties,
      byte[] body) throws IOException {
    long deliveryTag = envelope.getDeliveryTag();
    LOGGER.debug("Handle delivery: consumerTag: {}, deliveryTag: {}", consumerTag, deliveryTag);
    try {
      String contentType = properties.getContentType();
      if (decoder.willDecode(contentType)) {
        Object event = buildEvent(body);
        eventControl.fire(event);
      } else {
        LOGGER.error("Unable to process unknown message content type: {}", contentType);
      }
    } catch (Throwable t) {
      if (!autoAck) {
        LOGGER.error(
            "Consumer {}: Message {} could not be handled due to an exception during message processing",
            consumerTag, deliveryTag, t);
        channel.basicNack(deliveryTag, false, false);
        LOGGER.warn("Nacked message: consumerTag: {}, deliveryTag: {}", consumerTag, deliveryTag,
            t);
      }
      return;
    }
    if (!autoAck) {
      try {
        channel.basicAck(deliveryTag, false);
        LOGGER.debug("Acked message: consumerTag: {}, deliveryTag: {}", consumerTag, deliveryTag);
      } catch (IOException e) {
        LOGGER.error(
            "Consumer {}: Message {} was processed but could not be acknowledged due to an exception when sending the acknowledgement",
            consumerTag, deliveryTag, e);
        throw e;
      }
    }
  }

  @Override
  public void handleConsumeOk(String consumerTag) {
    LOGGER.debug("Consumer {}: Received consume OK", consumerTag);
  }

  @Override
  public void handleCancelOk(String consumerTag) {
    LOGGER.debug("Consumer {}: Received cancel OK", consumerTag);
  }

  @Override
  public void handleCancel(String consumerTag) throws IOException {
    LOGGER.debug("Consumer {}: Received cancel", consumerTag);
  }

  @Override
  public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
    LOGGER.debug("Consumer {}: Received shutdown signal: {}", consumerTag, sig.getMessage());
  }

  @Override
  public void handleRecoverOk(String consumerTag) {
    LOGGER.debug("Consumer {}: Received recover OK", consumerTag);
  }
}

package com.intel.jndn.mock;

import net.named_data.jndn.Data;
import net.named_data.jndn.Face;
import net.named_data.jndn.Interest;
import net.named_data.jndn.InterestFilter;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnInterestCallback;
import net.named_data.jndn.OnRegisterFailed;
import net.named_data.jndn.OnRegisterSuccess;
import net.named_data.jndn.OnTimeout;
import net.named_data.jndn.security.KeyChain;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * @author Andrew Brown, andrew.brown@intel.com
 */
public class MockForwarderTest {
  private static final Logger LOGGER = Logger.getLogger(MockForwarderTest.class.getName());

  @Test
  public void usage() throws Exception {
    Name prefix = new Name("/test");
    MockForwarder forwarder = new MockForwarder();
    Face a = forwarder.connect();
    Face b = forwarder.connect();

    LOGGER.info("Registering prefix: " + prefix);
    final CountDownLatch response1 = new CountDownLatch(1);
    a.registerPrefix(prefix, new OnInterestCallback() {
      @Override
      public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {
        LOGGER.info("Received interest: " + interest.toUri());
        try {
          face.putData(new Data(interest.getName()));
          LOGGER.info("Sent data to interest: " + interest.getName());
        } catch (IOException e) {
          LOGGER.info("Failed to send data for: " + interest.toUri());
        }
      }
    }, new OnRegisterFailed() {
      @Override
      public void onRegisterFailed(Name prefix) {
        LOGGER.severe("Failed to register prefix for: " + prefix);
        response1.countDown();
      }
    }, new OnRegisterSuccess() {
      @Override
      public void onRegisterSuccess(Name prefix, long registeredPrefixId) {
        LOGGER.info("Prefix registered: " + prefix);
        response1.countDown();
      }
    });
    a.processEvents();
    response1.await(1, TimeUnit.SECONDS);
    assertEquals(0, response1.getCount());

    LOGGER.info("Sending interest to prefix: " + prefix);
    final CountDownLatch response2 = new CountDownLatch(1);
    final AtomicBoolean received = new AtomicBoolean(false);
    b.expressInterest(prefix, new OnData() {
      @Override
      public void onData(Interest interest, Data data) {
        LOGGER.info("Received data: " + data.getName());
        response2.countDown();
        received.set(true);
      }
    }, new OnTimeout() {
      @Override
      public void onTimeout(Interest interest) {
        LOGGER.info("Failed to receive data for interest: " + interest.toUri());
        response2.countDown();
      }
    });
    b.processEvents();
    a.processEvents();
    b.processEvents();
    a.processEvents();

    response2.await(1, TimeUnit.SECONDS);
    assertTrue(received.get());
  }
}
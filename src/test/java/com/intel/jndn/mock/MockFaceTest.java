/*
 * jndn-mock
 * Copyright (c) 2015, Intel Corporation.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms and conditions of the GNU Lesser General Public License,
 * version 3, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details.
 */
package com.intel.jndn.mock;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import net.named_data.jndn.*;
import net.named_data.jndn.encoding.EncodingException;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.util.Blob;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test MockFace functionality
 */
public class MockFaceTest {

  @Before
  public void setup() throws SecurityException {
    face = new MockFace();
    counter = 0;
    recvData = null;
    isTimeout = false;
    exception = null;
  }

  @Test
  public void testExpressingAnInterest() throws IOException, EncodingException, InterruptedException {
    // make request
    expressInterest("/test/with/responses");

    run(2);

    // add response (after face is connectd)
    Data response = new Data(new Name("/test/with/responses"));
    response.setContent(new Blob("..."));
    face.receive(response);

    run(20);

    assertNotNull(recvData);
    assertEquals(isTimeout, false);
    assertEquals(recvData.getName().toString(), "/test/with/responses");
    assertEquals(recvData.getContent().buf(), new Blob("...").buf());
  }

  @Test
  public void testExpressingAnInterestLongerThanTheRegisteredPrefix() throws Exception {
    final CountDownLatch latch = new CountDownLatch(1);
    Name prefix = new Name("/test/short/prefix");
    OnInterestCallback callback = new OnInterestCallback() {
      @Override
      public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {
        try {
          face.putData(new Data(interest.getName().append("response")));
        } catch (IOException e) {
          fail("Responding with a data packet must succeed");
        }
      }
    };

    registerPrefix(face, prefix, callback, latch);
    latch.await(2, TimeUnit.SECONDS);

    expressInterest(prefix.append("request").toUri());
    run(3);

    assertNotNull(recvData);
    assertEquals(isTimeout, false);
    assertEquals(prefix.append("request").append("response"), recvData.getName());
  }

  @Test
  public void testExpressingAnInterestAfterConfiguration() throws IOException, EncodingException, InterruptedException {
    // add response (before face is connected)
    Data response = new Data(new Name("/test/with/responses"));
    response.setContent(new Blob("..."));
    face.receive(response);

    // make request
    expressInterest("/test/with/responses");

    run(20);

    assertNotNull(recvData);
    assertEquals(isTimeout, false);
    assertEquals(recvData.getName().toString(), "/test/with/responses");
    assertEquals(recvData.getContent().buf(), new Blob("...").buf());
  }

  @Test
  public void testInterestTimeouts() throws IOException, EncodingException, InterruptedException {
    // make request
    expressInterest("/some/name");

    run(20);

    assertEquals(recvData, null);
    assertEquals(isTimeout, true);
  }

  @Test
  public void testPrefixRegistration() throws IOException, SecurityException, EncodingException, InterruptedException {
    class State {
      boolean regFailed = false;
      boolean regSucceed = false;
    }
    final State state = new State();

    logger.info("Register prefix: /test/with/handlers");
    face.registerPrefix(new Name("/test/with/handlers"), new OnInterestCallback() {
      @Override
      public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {
        logger.info("Received interest, responding: " + interest.getName().toUri());
        Data response = new Data(new Name("/test/with/handlers"));
        response.setContent(new Blob("..."));
        try {
          face.putData(response);
        } catch (IOException e) {
          exception = e;
        }
        counter++;
      }
    }, new OnRegisterFailed() {
      @Override
      public void onRegisterFailed(Name prefix) {
        logger.info("Prefix registration fails: " + prefix);
        state.regFailed = true;
        counter++;
      }
    }, new OnRegisterSuccess() {
      @Override
      public void onRegisterSuccess(Name prefix, long registeredPrefixId) {
        logger.info("Prefix registration succeed: " + prefix);
        state.regSucceed = true;
        counter++;
      }
    });

    run(100, 1);
    assertTrue(state.regSucceed);
    assertFalse(state.regFailed);

    // make request
    face.receive(new Interest(new Name("/test/with/handlers")));

    run(100, 2);

    assertNull(exception);

    assertEquals(face.sentData.size(), 1);
    assertFalse(isTimeout);
    assertEquals("/test/with/handlers", face.sentData.get(0).getName().toString());
    assertEquals(new Blob("...").buf(), face.sentData.get(0).getContent().buf());
  }

  @Test
  public void testThatTransportConnectsOnPrefixRegistration() throws IOException, SecurityException {
    assertFalse(face.getTransport().getIsConnected());
    face.registerPrefix(new Name("/fake/prefix"), (OnInterestCallback) null, (OnRegisterFailed) null,
        (OnRegisterSuccess) null);
    assertTrue(face.getTransport().getIsConnected());
  }

  @Test
  public void testInterestFilters() throws IOException, SecurityException, EncodingException, InterruptedException {
    class State {
      boolean regFailed = false;
      boolean regSucceed = false;
    }
    final State state = new State();

    // connect transport
    face.registerPrefix(new Name("/fake/prefix"), (OnInterestCallback) null, new OnRegisterFailed() {
      @Override
      public void onRegisterFailed(Name prefix) {
        state.regFailed = true;
        counter++;
      }
    }, new OnRegisterSuccess() {
      @Override
      public void onRegisterSuccess(Name prefix, long registeredPrefixId) {
        state.regSucceed = true;
        counter++;
      }
    });

    // set filter
    face.setInterestFilter(new InterestFilter("/a/b"), new OnInterestCallback() {
      @Override
      public void onInterest(Name prefix, Interest interest, Face face, long interestFilterId, InterestFilter filter) {
        counter++;
      }
    });

    face.receive(new Interest(new Name("/a/b")).setInterestLifetimeMilliseconds(100));

    run(10, 2);

    assertEquals(2, counter);
    assertTrue(state.regSucceed);
    assertFalse(state.regFailed);
  }

  /////////////////////////////////////////////////////////////////////////////

  private void run(int limit, int maxCounter) throws IOException, EncodingException, InterruptedException {
    // process face until a response is received
    int allowedLoops = limit;
    while (counter < maxCounter && allowedLoops > 0) {
      allowedLoops--;
      face.processEvents();
      Thread.sleep(100);
    }
  }

  private void run(int limit) throws IOException, EncodingException, InterruptedException {
    run(limit, 1);
  }

  private void expressInterest(String name) throws IOException {
    logger.info("Express interest: " + name);
    face.expressInterest(new Interest(new Name(name)).setInterestLifetimeMilliseconds(1000), new OnData() {
      @Override
      public void onData(Interest interest, Data data) {
        counter++;
        logger.fine("Received data");
        recvData = data;
      }
    }, new OnTimeout() {
      @Override
      public void onTimeout(Interest interest) {
        logger.fine("Received timeout");
        counter++;
        isTimeout = true;
      }
    });
  }

  private void registerPrefix(Face face, Name prefix, OnInterestCallback onInterest, final CountDownLatch latch) throws IOException, SecurityException, EncodingException, InterruptedException {
    face.registerPrefix(prefix, onInterest, new OnRegisterFailed() {
      @Override
      public void onRegisterFailed(Name prefix) {
        fail("Registration must succeed");
      }
    }, new OnRegisterSuccess() {
      @Override
      public void onRegisterSuccess(Name prefix, long registeredPrefixId) {
        latch.countDown();
      }
    });

    run(3);
  }

  /////////////////////////////////////////////////////////////////////////////

  private static final Logger logger = Logger.getLogger(MockFaceTest.class.getName());
  private MockFace face;
  private int counter;
  private Data recvData = null;
  private boolean isTimeout = false;
  private Exception exception = null;
}

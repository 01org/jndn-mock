/*
 * jndn-mock
 * Copyright (c) 2016, Intel Corporation.
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

package com.intel.jndn.mock.forwarder;

import com.intel.jndn.mock.MockForwarder;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Andrew Brown, andrew.brown@intel.com
 */
public class FibImpl implements MockForwarder.Fib {

  private final ConcurrentHashMap<Name, MockForwarder.FibEntry> fib = new ConcurrentHashMap<>();

  @Override
  public void add(MockForwarder.FibEntry entry) {
    fib.put(entry.getPrefix(), entry);
  }

  public List<MockForwarder.FibEntry> find(Interest interest) {
    ArrayList<MockForwarder.FibEntry> entries = new ArrayList<>();
    for (int i = interest.getName().size(); i >= 0; i--) {
      Name prefix = interest.getName().getPrefix(i);
      MockForwarder.FibEntry entry = fib.get(prefix);
      if (entry != null) {
        entries.add(entry);
        if (!entry.getFlags().getChildInherit() || entry.getFlags().getCapture()) {
          break;
        }
      }
    }
    return entries;
  }
}

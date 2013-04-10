// Copyright 2012 Cloudera Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.cloudera.impala.service;

import java.io.File;

import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudera.impala.analysis.Expr;
import com.cloudera.impala.catalog.PrimitiveType;
import com.cloudera.impala.common.InternalException;
import com.cloudera.impala.thrift.TExpr;
import com.cloudera.impala.thrift.TQueryGlobals;
import com.google.common.base.Preconditions;

/**
 * This class provides the Impala executor functionality to the FE.
 * fe-support.cc implements all the native calls.
 * If the planner is executed inside Impalad, Impalad would have registered all the JNI
 * native functions already. There's no need to load the shared library.
 * For unit test (mvn test), load the shared library because the native function has not
 * been loaded yet.
 */
public class FeSupport {
  private final static Logger LOG = LoggerFactory.getLogger(FeSupport.class);

  private static boolean loaded = false;

  public native static boolean NativeEvalPredicate(byte[] thriftPredicate,
      byte[] thriftQueryGlobals);

  public static boolean EvalPredicate(Expr pred, TQueryGlobals queryGlobals)
      throws InternalException {
    Preconditions.checkState(pred.getType() == PrimitiveType.BOOLEAN);
    TExpr thriftPred = pred.treeToThrift();
    TSerializer serializer = new TSerializer(new TBinaryProtocol.Factory());
    try {
      return FeSupport.EvalPredicate(serializer.serialize(thriftPred),
          serializer.serialize(queryGlobals));
    } catch (TException e) {
      // this should never happen
      throw new InternalException("couldn't execute predicate " + pred.toSql(), e);
    }
  }

  private static boolean EvalPredicate(byte[] thriftPredicate, byte[] thriftQueryGlobals) {
    try {
      return NativeEvalPredicate(thriftPredicate, thriftQueryGlobals);
    } catch (UnsatisfiedLinkError e) {
      loadLibrary();
    }
    return NativeEvalPredicate(thriftPredicate, thriftQueryGlobals);
  }

  /**
   * This function should only be called explicitly by the FeSupport to ensure that
   * native functions are loaded.
   */
  private static synchronized void loadLibrary() {
    if (loaded) {
      return;
    }
    loaded = true;

    // Search for libfesupport.so in all library paths.
    String libPath = System.getProperty("java.library.path");
    LOG.info("trying to load libfesupport.so from " + libPath);
    String[] paths = libPath.split(":");
    boolean found = false;
    for (String path : paths) {
      String filePath = path + File.separator + "libfesupport.so";
      File libFile = new File(filePath);
      if (libFile.exists()) {
        LOG.info("loading " + filePath);
        System.load(filePath);
        found = true;
        break;
      }
    }
    if (!found) {
      LOG.error("Failed to load libfesupport.so from given java.library.paths ("
          + libPath + ").");
    }
  }
}


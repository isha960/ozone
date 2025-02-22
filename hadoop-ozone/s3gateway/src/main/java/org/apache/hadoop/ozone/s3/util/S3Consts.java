/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.hadoop.ozone.s3.util;

import org.apache.hadoop.hdds.annotation.InterfaceAudience;

import java.util.regex.Pattern;

/**
 * Set of constants used for S3 implementation.
 */
@InterfaceAudience.Private
public final class S3Consts {

  //Never Constructed
  private S3Consts() {

  }

  public static final String COPY_SOURCE_HEADER = "x-amz-copy-source";
  public static final String COPY_SOURCE_HEADER_RANGE =
      "x-amz-copy-source-range";
  public static final String STORAGE_CLASS_HEADER = "x-amz-storage-class";
  public static final String ENCODING_TYPE = "url";

  // Constants related to Range Header
  public static final String COPY_SOURCE_IF_PREFIX = "x-amz-copy-source-if-";
  public static final String COPY_SOURCE_IF_MODIFIED_SINCE =
      COPY_SOURCE_IF_PREFIX + "modified-since";
  public static final String COPY_SOURCE_IF_UNMODIFIED_SINCE =
      COPY_SOURCE_IF_PREFIX + "unmodified-since";

  // Constants related to Range Header
  public static final String RANGE_HEADER_SUPPORTED_UNIT = "bytes";
  public static final String RANGE_HEADER = "Range";
  public static final String ACCEPT_RANGE_HEADER = "Accept-Ranges";
  public static final String CONTENT_RANGE_HEADER = "Content-Range";


  public static final Pattern RANGE_HEADER_MATCH_PATTERN =
      Pattern.compile("bytes=(?<start>[0-9]*)-(?<end>[0-9]*)");

  //Error code 416 is Range Not Satisfiable
  public static final int RANGE_NOT_SATISFIABLE = 416;

  public static final String S3_XML_NAMESPACE = "http://s3.amazonaws" +
      ".com/doc/2006-03-01/";

  public static final String CUSTOM_METADATA_HEADER_PREFIX = "x-amz-meta-";

  public static final String DECODED_CONTENT_LENGTH_HEADER =
      "x-amz-decoded-content-length";

}

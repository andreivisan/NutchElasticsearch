/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.nutch.protocol.file;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;

import org.apache.nutch.crawl.CrawlDatum;
import org.apache.nutch.net.protocols.Response;
import org.apache.nutch.protocol.Content;
import org.apache.nutch.protocol.Protocol;
import org.apache.nutch.protocol.ProtocolOutput;
import org.apache.nutch.protocol.ProtocolStatus;
import org.apache.nutch.protocol.RobotRulesParser;
import org.apache.nutch.util.NutchConfiguration;

import crawlercommons.robots.BaseRobotRules;

/**
 * This class is a protocol plugin used for file: scheme.
 * It creates {@link FileResponse} object and gets the content of the url from it.
 * Configurable parameters are {@code file.content.limit} and {@code file.crawl.parent} 
 * in nutch-default.xml defined under "file properties" section.
 *
 * @author John Xing
 */
public class File implements Protocol {

  public static final Logger LOG = LoggerFactory.getLogger(File.class);

  static final int MAX_REDIRECTS = 5;

  int maxContentLength;
  boolean crawlParents;

  private Configuration conf;

  public File() {}

  /**
   * Set the {@link Configuration} object
   */
  public void setConf(Configuration conf) {
    this.conf = conf;
    this.maxContentLength = conf.getInt("file.content.limit", 64 * 1024);
    this.crawlParents = conf.getBoolean("file.crawl.parent", true);
  }

  /**
   * Get the {@link Configuration} object
   */
  public Configuration getConf() {
    return this.conf;
  }
  
  /** 
   * Set the length after at which content is truncated. 
   */
  public void setMaxContentLength(int maxContentLength) {
    this.maxContentLength = maxContentLength;
  }

  /** 
   * Creates a {@link FileResponse} object corresponding to the url and 
   * return a {@link ProtocolOutput} object as per the content received
   * 
   * @param url Text containing the url
   * @param datum The CrawlDatum object corresponding to the url
   * 
   * @return {@link ProtocolOutput} object for the content of the file indicated by url
   */
  public ProtocolOutput getProtocolOutput(Text url, CrawlDatum datum) {
    String urlString = url.toString();
    try {
      URL u = new URL(urlString);
  
      int redirects = 0;
  
      while (true) {
        FileResponse response;
        response = new FileResponse(u, datum, this, getConf());   // make a request
  
        int code = response.getCode();
  
        if (code == 200) {                          // got a good response
          return new ProtocolOutput(response.toContent());              // return it
  
        } else if (code == 304) {                   // got not modified
          return new ProtocolOutput(response.toContent(), ProtocolStatus.STATUS_NOTMODIFIED);

        } else if (code == 401) {                   // access denied / no read permissions
          return new ProtocolOutput(response.toContent(), new ProtocolStatus(ProtocolStatus.ACCESS_DENIED));

        } else if (code == 404) {                   // no such file
          return new ProtocolOutput(response.toContent(), ProtocolStatus.STATUS_NOTFOUND);

        } else if (code >= 300 && code < 400) {     // handle redirect
          if (redirects == MAX_REDIRECTS)
            throw new FileException("Too many redirects: " + url);
          u = new URL(response.getHeader("Location"));
          redirects++;                
          if (LOG.isTraceEnabled()) {
            LOG.trace("redirect to " + u); 
          }
  
        } else {                                    // convert to exception
          throw new FileError(code);
        }
      } 
    } catch (Exception e) {
      e.printStackTrace();
      return new ProtocolOutput(null, new ProtocolStatus(e));
    }
  }

  /** 
   * Quick way for running this class. Useful for debugging.
   */
  public static void main(String[] args) throws Exception {
    int maxContentLength = Integer.MIN_VALUE;
    String logLevel = "info";
    boolean dumpContent = false;
    String urlString = null;

    String usage = "Usage: File [-logLevel level] [-maxContentLength L] [-dumpContent] url";

    if (args.length == 0) {
      System.err.println(usage);
      System.exit(-1);
    }
      
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-logLevel")) {
        logLevel = args[++i];
      } else if (args[i].equals("-maxContentLength")) {
        maxContentLength = Integer.parseInt(args[++i]);
      } else if (args[i].equals("-dumpContent")) {
        dumpContent = true;
      } else if (i != args.length-1) {
        System.err.println(usage);
        System.exit(-1);
      } else
        urlString = args[i];
    }

    File file = new File();
    file.setConf(NutchConfiguration.create());

    if (maxContentLength != Integer.MIN_VALUE) // set maxContentLength
      file.setMaxContentLength(maxContentLength);

    // set log level
    //LOG.setLevel(Level.parse((new String(logLevel)).toUpperCase()));

    Content content = file.getProtocolOutput(new Text(urlString), new CrawlDatum()).getContent();

    System.err.println("Content-Type: " + content.getContentType());
    System.err.println("Content-Length: " +
                       content.getMetadata().get(Response.CONTENT_LENGTH));
    System.err.println("Last-Modified: " +
                       content.getMetadata().get(Response.LAST_MODIFIED));
    if (dumpContent) {
      System.out.print(new String(content.getContent()));
    }

    file = null;
  }

  /** 
   * No robots parsing is done for file protocol. 
   * So this returns a set of empty rules which will allow every url.
   */
  public BaseRobotRules getRobotRules(Text url, CrawlDatum datum) {
    return RobotRulesParser.EMPTY_RULES;
  }
}


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

package org.apache.nutch.parse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.nutch.crawl.CrawlDatum;
import org.apache.nutch.crawl.SignatureFactory;
import org.apache.nutch.protocol.Content;
import org.apache.nutch.protocol.Protocol;
import org.apache.nutch.protocol.ProtocolFactory;
import org.apache.nutch.protocol.ProtocolOutput;
import org.apache.nutch.util.NutchConfiguration;
import org.apache.nutch.util.URLUtil;
import org.apache.nutch.util.StringUtil;

/**
 * Parser checker, useful for testing parser.
 * It also accurately reports possible fetching and 
 * parsing failures and presents protocol status signals to aid 
 * debugging. The tool enables us to retrieve the following data from 
 * any url:
 * <ol>
 * <li><tt>contentType</tt>: The URL {@link org.apache.nutch.protocol.Content} type.</li>
 * <li><tt>signature</tt>: Digest is used to identify pages (like unique ID) and is used to remove
 * duplicates during the dedup procedure. 
 * It is calculated using {@link org.apache.nutch.crawl.MD5Signature} or
 * {@link org.apache.nutch.crawl.TextProfileSignature}.</li>
 * <li><tt>Version</tt>: From {@link org.apache.nutch.parse.ParseData}.</li>
 * <li><tt>Status</tt>: From {@link org.apache.nutch.parse.ParseData}.</li>
 * <li><tt>Title</tt>: of the URL</li>
 * <li><tt>Outlinks</tt>: associated with the URL</li>
 * <li><tt>Content Metadata</tt>: such as <i>X-AspNet-Version</i>, <i>Date</i>,
 * <i>Content-length</i>, <i>servedBy</i>, <i>Content-Type</i>, <i>Cache-Control</>, etc.</li>
 * <li><tt>Parse Metadata</tt>: such as <i>CharEncodingForConversion</i>,
 * <i>OriginalCharEncoding</i>, <i>language</i>, etc.</li>
 * <li><tt>ParseText</tt>: The page parse text which varies in length depdnecing on 
 * <code>content.length</code> configuration.</li>
 * </ol>
 * @author John Xing
 */

public class ParserChecker implements Tool {

  public static final Logger LOG = LoggerFactory.getLogger(ParserChecker.class);
  private Configuration conf;

  public ParserChecker() {
  }

  public int run(String[] args) throws Exception {
    boolean dumpText = false;
    boolean force = false;
    String contentType = null;
    String url = null;

    String usage = "Usage: ParserChecker [-dumpText] [-forceAs mimeType] url";

    if (args.length == 0) {
      LOG.error(usage);
      return (-1);
    }

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-forceAs")) {
        force = true;
        contentType = args[++i];
      } else if (args[i].equals("-dumpText")) {
        dumpText = true;
      } else if (i != args.length - 1) {
        LOG.error(usage);
        System.exit(-1);
      } else {
        url = URLUtil.toASCII(args[i]);
      }
    }

    if (LOG.isInfoEnabled()) {
      LOG.info("fetching: " + url);
    }

    ProtocolFactory factory = new ProtocolFactory(conf);
    Protocol protocol = factory.getProtocol(url);
    ProtocolOutput output = protocol.getProtocolOutput(new Text(url), new CrawlDatum());
    
    if (!output.getStatus().isSuccess()) {
      System.err.println("Fetch failed with protocol status: " + output.getStatus());
      return (-1);
    }
    
    Content content = output.getContent();

    if (content == null) {
      LOG.error("No content for " + url);
      return (-1);
    }

    if (force) {
      content.setContentType(contentType);
    } else {
      contentType = content.getContentType();
    }

    if (contentType == null) {
      LOG.error("Failed to determine content type!");
      return (-1);
    }

    if (ParseSegment.isTruncated(content)) {
      LOG.warn("Content is truncated, parse may fail!");
    }

    ParseResult parseResult = new ParseUtil(conf).parse(content);

    if (parseResult == null) {
      LOG.error("Problem with parse - check log");
      return (-1);
    }

    // Calculate the signature
    byte[] signature = SignatureFactory.getSignature(getConf()).calculate(content, parseResult.get(new Text(url)));
    
    if (LOG.isInfoEnabled()) {
      LOG.info("parsing: " + url);
      LOG.info("contentType: " + contentType);
      LOG.info("signature: " + StringUtil.toHexString(signature));
    }

    for (java.util.Map.Entry<Text, Parse> entry : parseResult) {
      Parse parse = entry.getValue();
      LOG.info("---------\nUrl\n---------------\n");
      System.out.print(entry.getKey());
      LOG.info("\n---------\nParseData\n---------\n");
      System.out.print(parse.getData().toString());
      if (dumpText) {
        LOG.info("---------\nParseText\n---------\n");
        System.out.print(parse.getText());
      }
    }

    return 0;
  }

  @Override
  public Configuration getConf() {
    return conf;
  }

  @Override
  public void setConf(Configuration c) {
    conf = c;
  }

  public static void main(String[] args) throws Exception {
    int res = ToolRunner.run(NutchConfiguration.create(), new ParserChecker(),
        args);
    System.exit(res);
  }

}

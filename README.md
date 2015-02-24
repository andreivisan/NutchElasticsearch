This repository contains all the configurations you need in order to make Apache Nutch 1.8 work with Elasticsearch 0.90.11.

What you will need to change:
- Inside conf folder of Apache Nutch there is a file called nutch-site.txt. 
- Modify the cluster and index values accordingly to your Elasticsearch settings.
- Inside Elasticearch folder there is a conf folder as well. 
- Modify elasticsearch.yml file accordingly to your settings.

For more instructions on how to configure Elasticsearch you can visit their webpage.

Note that the Apache Nutch is already compiled, which means you don't need to runt 'ant' command inside it but you will need to chage the nutch-site.xml file inside the runtime/local/conf folder as well.

Inside ElasticSearch you will need to create an index. The command for that is: `create index: curl -XPUT 'http://localhost:9200/index_name/'`

To check that the index was created use: check status: `curl -XGET 'http://localhost:9200/_mapping?pretty=1'`

Inside nutch home folder under `runtime/local` there is a folder called `urls`. Inside it there is a file called `seed.txt`. This file contains the urls you want to crawl. I defaulted it to nutch home page. You can change it with whatever URLs you need to crawl.

Now, in order to crawl and index run the following command: `bin/crawl urls/ TestCrawl -depth 2 -topN 50`

Read Nutch tutorial homepage in order to understand the parameters inside the `bin/crawl` scrip: 
- http://wiki.apache.org/nutch/NutchTutorial#A3._Crawl_your_first_website.

To check the indexed fields you can use the command: `curl -XGET 'http://localhost:9200/_mapping?pretty=1'`

To check the indexed data you can use the command: `curl -XGET 'http://localhost:9200/_search?pretty=1'`.

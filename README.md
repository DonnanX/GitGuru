# GitGuru🌟

## 简介🚀

GitGuru是一个关于GitHub的数据应用。

GitGuru存在的价值是让开发者能够更加方便的去了解GitHub的信息。

GitGuru目前提供了以下功能：

- 根据自定义算法对用户的技术进行打分。（满分100）
- GitHub Chat
  - 通过Chat的方式了解某个GitHub用户的信息。
  - 通过Chat的方式了解某个GitHub仓库的内容。
- ...

## 特性✨

数据加载和技术评估：

- GitGuru会定时的从GitHub获取数据，并根据自定义算法为其打分。
- GitGuru会优先加载平台用户的GitHub关系图上的GitHub用户，并根据自定义算法为其打分。

GitHub Chat:

- 利用Spring AI + Tool Calling + RAG实现的大模型助手。

## 如何使用🛠️


1. 需要提供百炼平台的大模型API-Key。
2. 需要提供GitHub API的API-Key。
3. 部署Redis。
4. 部署ElasticSearch。

<<<<<<< HEAD
﻿# ef-orm
===================================
=======
# GeeQuery
----
>>>>>>> 3a7ce895f1dabdcece14c96ccd131e45bc76dd98

A Simple OR-Mapping framework on multiple databases.


* 使用手册(中文)<br>
http://geequery.github.io/ef-orm/manual/EF-ORM-user-guide.docx
<br>
* 使用示例工程
https://github.com/GeeQuery/ef-orm/tree/master/orm-tutorial

EF-ORM是一个轻量，便捷的Java ORM框架。并且具备若干企业级的应用特性，如分库分表、JTA事务等。

* 代码生成插件for eclipse（请在eclipse中Help/Install new software后输入地址并安装）
http://geequery.github.io/plugins/1.3.x/

-----------
###特点一
EF的设计的一个主要目的是提高开发效率，减少编码工作，让开发者“零配置”“少编码”的操作数据库大部分功能。

例如：数据库查询条件的传入问题是所有ORM框架都不能回避的一个问题，所以我经常在想——既然我们可以用向DAO传入一个Entity来实现插入操作，为什么就不能用同样的方法来描述一个不以主键为条件的update/select/delete操作？为什么DAO的接口参数老是变来变去？为什么很多应用中，自行设计开发类来描述各种业务查询条件才能传入DAO？为什么我们不能在数据访问层上花费更少的时间和精力?

　　JPA1.0和早期的H框架，其思想是将关系型数据库抽象为对象池，这极大的限制了本来非常灵活的SQL语句的发挥空间。而本质上，当我们调用某H框架的session.get、session.load、session.delete时，我们是想传递一个以对象形式表达的数据库操作请求。只不过某H框架要求（并且限制）我们将其视作纯粹的“单个”对象而已。JPA 2.0为了弥补JPA1.0的不足，才将这种Query的思想引入为框架中的另一套查询体系——Criteria API。事实上针对单个对象的get/load/persist/save/update/merge/saveOrUpdate API和Criteria API本来就为一体，只不过是历史的原因被人为割裂成为两套数据库操作API罢了。

　　因此，对于关系型数据库而言——Entity和Query是一体两面的事物，所谓Query，可以包含各种复杂的查询条件，甚至可以作为一个完整的SQL操作请求的描述。为此，EF彻底将Entity和Query绑在了一起。这种思想，使得——

1. 开发人员需要编写的类更少。开发人员无需编写其他类来描述复杂的SQL查询条件。也无需编写代码将这些查询条件转换为SQL/HQL/JPQL。DAO层也不会有老要改来改去的接口和API，几乎可以做到零编码。

1. 对单个对象进行CRUD的操作API现在和Criteria API合并在一起。Session对象可以直接提供原本要Criteria API才能提供实现的功能。API大大简化。

1. IQueryableEntity允许你将一个实体直接变化为一个查询（Query），在很多时候可以用来完成复杂条件下的数据查询。比如 ‘in (?,?,?)’， ‘Between 1 and 10’之类的条件。
xxQL有着拼装语句可读性差、编译器无法检查、变更维护困难等问题，但是却广受开发人员欢迎。这多少有历史原因，也有Criteria API设计上过于复杂的因素。两者一方是极端灵活但维护困难，一方是严谨强大而学习和编写繁琐，两边都是极端。事实上JPA的几种数据查询方式存在青黄不接的问题。选择查询语言xxQL，项目面临后续维护困难，跨数据库移植性差；选择Criteria API，代码臃肿，操作繁琐，很多人望而却步。EF的设计思想是使人早日摆脱拼装SQL/HQL/JPQL的困扰，而是用（更精简易用的）Criteria API来操作数据库。

1. 基于轻量级Criteria API的操作方式，使得对数据库的变更和重构变得非常轻松，解决了SQL语句多对软件维护和移植造成产生的不利影响。

* 阅读推荐：第3、4章


###特点二，将SQL的使用发挥到极致，解决SQL拼凑问题、数据库移植问题
大部分OLTP应用系统到最后都不免要使用SQL/JPQL，然而没有一个很好的方法解决SQL在多种数据库下兼容性的问题。
EF-ORM中采用了独特的SQL解析和改写技术，能够主动检查并确保SQL语句或者SQL片段在各个数据库上的兼容性。

EF中除了Criteria API以外，可以直接使用“SQL语句”或者“SQL片段”。但是这些SQL语句并不是直接传送给JDBC驱动的，而是
有着一个数据库方言层，经过方言层处理的SQL语句，就具备了在当前数据库上正确操作的能力。这相当于提供了一种能跨数据库操作的SQL语言。(E-SQL)
 E-SQL不但解决了异构数据库的语法问题、函数问题、特殊的写法问题，还解决了动态SQL问题、绑定变量扩展等特性。
 对于各种常用SQL函数和运算符，都可以自动转换为当前数据库支持的方言来操作。其函数支持也要多于HQL支持的函数。

* 阅读推荐：第7、8章

   
###特点三，可能是业界最快的ORM框架.
得益于ASM的动态代码生成技术，部分耗时操作通过动态代码固化为硬编码实现，EF-ORM的大部分操作性能要超过已知的其他框架。
     实际性能测试表明，EF的大部分操作都要快于Hiberante和MyBatis， 部分操作速度甚至数十倍于上述框架。
EF在极限插入模式下，甚至刷新了每秒10万条写入的记录。远远超过了其他框架。

一个初步的性能测试：<br>
测试代码：http://geequery.github.io/ef-orm/manual/performance-test.rar
测试报告：http://geequery.github.io/ef-orm/manual/performance-compare.docx

* 阅读推荐：第9、17章


###特点四，分库分表
开发过程中参照了Hibernate Shards、Alibaba TDDL、Cobar等框架，也是基于词法分析器来提取SQL参数，并计算路由。
   能支持分库维度含糊等场景下的分库分表。以及包括多库多表下的 order by , distinct, group by, having等操作。

* 阅读推荐：第10章

###特点五，常用DDL操作的封装
从数据库元数据访问，到建表，创建约束，创建sequence等各种DDL操作进行了封装，用户无需编写各种SQL，可以直接通过API操作数据库结构。
尤其是ALTER TABLE等修改数据库的语句，各种不同的RDBMS都有较大语法差异。

###特点六、解决各种跨RDBMS的移植问题
1、DML操作、自增值处理与返回、查询这些不同数据库操作差异很大的东西，都了统一的封装。
2、DDL操作、建表、删表、trunacte，Sequence创建和TABLE模拟Sequence等，都做了支持。
3、对SQL语法操作和函数的改写与支持。

##其他特性
-----------
### 轻量
该框架对应用环境、连接池、 是否为J2EE应用等没有特殊要求。可以和EJB集成，也可与Spring集成，也可以单独使用。整个框架只有两个JAR包，模块和功能都较为轻量。

### 依赖少
整个框架只有三个jar库。间接依赖仅有commons-lang, slf4j等7个通用库，作为一个ORM框架，对第三方依赖极小。

### 简单直接的API
框架的API设计直接面向数据库操作，不绕弯子，开发者只需要数据库基本知识，不必学习大量新的操作概念即可使用API完成各种DDL/DML操作。
最大限度利用编译器减少编码错误的可能性	API设计和元数据模型（meta-model）的使用，使得常规的数据库查询都可以直接通过Criteria API来完成，无需使用任何JPQL/HQL/SQL。可以让避免用户犯一些语法、拼写等错误。

### JPA2规范兼容
使用JPA 2.0规范的标准注解方式来定义和操作对象。（但整个ORM不是完整的JPA兼容实现）

### 更高的性能
依赖于ASM等静态字节码技术而不是CGlib，使得改善了代理性能；依赖于动态反射框架，内部数据处理上的开销几乎可以忽略。操作性能接近JDBC水平。对比某H开头的框架，在写入操作上大约领先30%，在大量数据读取上领先50%以上。

### 更多的性能调优手段
debug模式下提供了大量性能日志，帮您分析性能瓶颈所在。同时每个查询都可以针对batch、fetchSize、maxResult、缓存、级联操作类型等进行调整和开关，可以将性能调到最优。

### 可在主流数据库之间任意切换
支持Oracle、MySQL、Postgres、MSSQL、GBase、SQLite、HSQL、Derby等数据库。除了API方式下的操作能兼容各个数据库之外，就连SQL的本地化查询也能使之兼容。

### JMX动态调节
可以用JMX查看框架运行统计。框架的debug开关和其他参数都可以使用JMX动态调整。

### 动态表支持
表结构元数据的API也向用户开放，同时支持在使用过程中，灵活调整映射关系，因此用户可以用API动态的创建表结构的模型，从而实现各种动态类型和表的映射（例如POJO中包含一个Map，用于映射各种动态扩展的字段）

### 企业级特性支持
SQL分析，性能统计，分库分表，Oracle RAC支持，读写分离支持
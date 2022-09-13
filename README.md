## 使用 Calcite 对 SQL 进行血缘分析
本例是一个基础 Demo，借助 Calcite 解析 SQL 获取源表和结果表，各位可在此基础上进行扩展从而支持更多的场景。
#### 关于 Calcite
Apache Calcite 是一款开源的动态数据管理框架，它提供了标准的SQL语言、多种查询优化和连接各种数据源的能力，但不包括数据存储、处理数据的算法和存储元数据的存储库。

#### 实现思路
我们可以借助 Calcite SqlParser 解析器分析 SQL 并生成 AST 语法树，并通过访问 AST 的各个节点获取到我们想要的信息。

<img src="https://p3-sign.toutiaoimg.com/tos-cn-i-qvj2lq49k0/5fe15bd627234c1e8bd23c9947508663~noop.image?_iz=58558&from=article.pc_detail&x-expires=1663667464&x-signature=y3vER0mu5sNMOjNMtxGqxnoZUFU%3D">

在 Calcite 解析出来的 AST 是以 SqlNode 的形式表现的，一个 SqlNode 即是 AST 中的一个节点。SqlNode 有许多类型，我们关注的 Source 和 Sink 表表名在 AST 中会是一个 SqlIdentifier 的叶子结点。（注意：并非所有 SqlIdentifier 叶子结点都对应表名，列名也对应 SqlIdentifier）

在一条 SQL 中，最终出现表的引用的情况归结于以下两种情况：
- SELECT 语句的 FROM clause 中的直接引用
- JOIN 语句中 LEFT 和 RIGHT clause 中的直接引用

嵌套子查询的 SQL 语句中，最终进入到子查询的 AST 子树中，只要出现了对表的引用，一定会分解出以上两种结构。因此，对于一个 SqlIdentifier 类型的叶子节点，在以下两种情况下，该叶子结点就是一个表的引用：
- 父节点是 SqlSelect，且当前节点是父节点的 FROM 子句派生出的子节点
- 父节点是 SqlJoin（如果是 Lookup join 则节点为 SNAPSHOT 类型，需继续深入子节点）

另外，一种特殊的情况需要加以考虑。在 SQL 中 AS 常用作起别名，因而可能 SqlIdentifier 的父节点是 AS，而 AS 的父节点是 SELECT 或 JOIN。这种情况下，我们可以将 AS 看作一种 “转发” 结点，即 AS 的父节点和子节点忽略掉 AS 结点，直接构成父子关系。

从根结点开始遍历 AST，解析所有的子查询，找到符合上述两种情况的子结构，就可以提取出所有对表的引用。

### 核心代码
````
public static SqlBloodRes getDependencies(SqlNode sqlNode, SqlBloodRes res, Boolean fromOrJoin) {
        if (sqlNode.getKind() == JOIN) {
            SqlJoin sqlKind = (SqlJoin) sqlNode;
            getDependencies(sqlKind.getLeft(), res, true);
            getDependencies(sqlKind.getRight(), res, true);
        } else if (sqlNode.getKind() == IDENTIFIER) {
            if (fromOrJoin) {
                // 获取 source 表名
                res.getSourceTables().add(sqlNode.toString());
            }
        } else if (sqlNode.getKind() == AS) {
            SqlBasicCall sqlKind = (SqlBasicCall) sqlNode;
            if (sqlKind.getOperandList().size() >= 2) {
                getDependencies(sqlKind.getOperandList().get(0), res, fromOrJoin);
            }
        } else if (sqlNode.getKind() == INSERT) {
            SqlInsert sqlKind = (SqlInsert) sqlNode;
            // 获取 sink 表名
            res.setSinkTable(sqlKind.getTargetTable().toString());
            getDependencies(sqlKind.getSource(), res, false);
        } else if (sqlNode.getKind() == SELECT) {
            SqlSelect sqlKind = (SqlSelect) sqlNode;
            List<SqlNode> list = sqlKind.getSelectList().getList();
            for (SqlNode i : list) {
                getDependencies(i, res, false);
            }
            getDependencies(sqlKind.getFrom(), res, true);
        } else if (sqlNode.getKind() == SNAPSHOT) {
            // 处理 Lookup join 的情况
            SqlSnapshot sqlKind = (SqlSnapshot) sqlNode;
            getDependencies(sqlKind.getTableRef(), res, true);
        } else {
            // TODO 这里可根据需求拓展处理其他类型的 sqlNode
        }
        return res;
    }
````
更多信息可查看 [使用 Calcite 解析 SQL 获取源表和结果表](https://www.toutiao.com/article/7137180267675943435/)

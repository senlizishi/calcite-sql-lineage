package com.wxb.demo;

import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.impl.SqlParserImpl;

import java.util.List;

import static org.apache.calcite.sql.SqlKind.*;

/**
 * @author wxb
 * @date 2022-09-13
 */
public class Lineage {

    public static void main(String[] args) {
        String sql = "INSERT INTO catalog.database.table_c (`username`, `password`)\n" +
                "SELECT a.username, b.password\n" +
                "FROM catalog.database.table_a a\n" +
                "\tINNER JOIN catalog.database.table_b b ON a.uid = b.uid";
        // 在解析前可以对 SQL 语句进行预处理，比如将不支持的 && 替换为 AND， != 替换为 <>
        SqlParser.Config config =
                SqlParser.configBuilder()
                        .setParserFactory(SqlParserImpl.FACTORY)
                        .setLex(Lex.JAVA)
                        .setIdentifierMaxLength(256)
                        .build();
        // 创建解析器
        SqlParser sqlParser = SqlParser
                .create(sql, config);
        // 生成 AST 语法树
        SqlNode sqlNode;
        try {
            sqlNode = sqlParser.parseStmt();
        } catch (SqlParseException e) {
            throw new RuntimeException("使用 Calcite 进行语法分析发生了异常", e);
        }
        SqlBloodRes res = new SqlBloodRes();
        // 递归遍历语法树
        SqlBloodRes dependencies = getDependencies(sqlNode, res, false);
        System.out.println("Source 表为：" + dependencies.getSourceTables().toString());
        System.out.println("Sink 表为：" + dependencies.getSinkTable());
    }

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
}

package com.wxb.demo;


import lombok.Data;

import java.util.*;

/**
 * SQL 血缘关系响应结果
 *
 * @author weixubin
 * @date 2022-09-13
 */
@Data
public class SqlBloodRes {

    private List<String> sourceTables = new ArrayList<>();

    private String sinkTable;

}

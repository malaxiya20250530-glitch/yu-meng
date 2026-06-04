package com.yumeng.plugin;

import java.util.Map;

/**
 * 计算器插件
 */
public class CalculatorPlugin extends ToolPlugin {

    @Override
    public void onInstall() {
        tools().log("info", "计算器插件已就绪");
    }

    @Override
    public ToolResult onInvoke(String action, Map<String, Object> args) {
        if ("calc".equals(action)) {
            try {
                String expr = (String) args.getOrDefault("expr", "0");
                // 安全求值：仅支持 + - * / 和数字
                double result = safeEval(expr);
                return ToolResult.ok("result", result);
            } catch (Exception e) {
                return ToolResult.fail("计算错误: " + e.getMessage());
            }
        }
        return ToolResult.fail("计算器插件仅支持 calc 操作");
    }

    private double safeEval(String expr) {
        // 简易四则运算
        expr = expr.replaceAll("\\s+", "");
        // 先算乘除
        expr = evalMD(expr);
        // 再算加减
        return evalAS(expr);
    }

    private String evalMD(String s) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+\\.?\\d*)([*/])(\\d+\\.?\\d*)");
        java.util.regex.Matcher m = p.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            double a = Double.parseDouble(m.group(1));
            double b = Double.parseDouble(m.group(3));
            double r = m.group(2).equals("*") ? a * b : a / b;
            m.appendReplacement(sb, String.valueOf(r));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private double evalAS(String s) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(-?\\d+\\.?\\d*)([+-])(\\d+\\.?\\d*)");
        java.util.regex.Matcher m = p.matcher(s);
        if (!m.find()) return Double.parseDouble(s);
        double a = Double.parseDouble(m.group(1));
        double b = Double.parseDouble(m.group(3));
        double r = m.group(2).equals("+") ? a + b : a - b;
        return r;
    }
}

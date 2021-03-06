package org.dhatim.safesql.builder;

import org.dhatim.safesql.SafeSql;
import org.dhatim.safesql.SafeSqlBuilder;
import org.dhatim.safesql.SafeSqlUtils;

public class Literal implements Operand {
    
    private static final Literal NULL_LITERAL = new Literal(null) {
        @Override
        public void appendTo(SafeSqlBuilder builder) {
            builder.append("NULL");
        }
        
        @Override
        public SafeSql toSafeSql() {
            return SafeSqlUtils.fromConstant("NULL");
        }
    };

    private final String value;

    protected Literal(String value) {
        this.value = value;
    }
    
    @Deprecated
    public static Literal from(String value) {
        return of(value);
    }
    
    public static Literal of(String value) {
        return value == null ? NULL_LITERAL : new Literal(value);
    }
    
    @Override
    public void appendTo(SafeSqlBuilder builder) {
        builder.appendStringLiteral(value);
    }
    
}

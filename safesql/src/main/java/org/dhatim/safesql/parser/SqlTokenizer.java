package org.dhatim.safesql.parser;

import static org.dhatim.safesql.parser.SqlTokenizer.CharType.*;
import static org.dhatim.safesql.parser.SqlTokenizer.State.*;

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class SqlTokenizer {
    
    public enum TokenClass {
        KEYWORD, IDENTIFIER, QUOTED_IDENTIFIER, LITERAL, SYMBOL, WHITESPACE
    }

    public enum TokenType {
        KEYWORD                     (TokenClass.KEYWORD), 
        IDENTIFIER                  (TokenClass.IDENTIFIER), 
        QUOTED_IDENTIFIER           (TokenClass.QUOTED_IDENTIFIER), 
        UNICODE_QUOTED_IDENTIFIER   (TokenClass.QUOTED_IDENTIFIER), 
        STRING                      (TokenClass.LITERAL), 
        ESCAPED_STRING              (TokenClass.LITERAL), 
        UNICODE_STRING              (TokenClass.LITERAL), 
        DOLLAR_QUOTED_STRING        (TokenClass.LITERAL), 
        BITSTRING                   (TokenClass.LITERAL), 
        HEXSTRING                   (TokenClass.LITERAL), 
        NUMERIC                     (TokenClass.LITERAL),
        OPERATOR                    (TokenClass.SYMBOL), 
        WHITESPACE                  (TokenClass.WHITESPACE), 
        LINE_COMMENT                (TokenClass.WHITESPACE), 
        BLOCK_COMMENT               (TokenClass.WHITESPACE), 
        POSITIONAL_PARAMETER        (TokenClass.SYMBOL), 
        LPAREN                      (TokenClass.SYMBOL), 
        RPAREN                      (TokenClass.SYMBOL), 
        LBRACK                      (TokenClass.SYMBOL), 
        RBRACK                      (TokenClass.SYMBOL), 
        SEMI                        (TokenClass.SYMBOL), 
        COMMA                       (TokenClass.SYMBOL), 
        DOT                         (TokenClass.SYMBOL);
        
        private final TokenClass tokenClass;
        
        private TokenType(TokenClass tokenClass) {
            this.tokenClass = tokenClass;
        }
        
        public TokenClass getTokenClass() {
            return tokenClass;
        }
    }

    enum CharType {
        UNKNOWN, LETTER, UNDERSCORE, DIGIT, DOLLAR, QUOTE, DOUBLE_QUOTE, WHITESPACE, LPAREN, RPAREN, LBRACK, RBRACK, AMPERSAND, SEMI, COMMA, DOT, PLUS, MINUS, 
        ASTERISK, DIV, LT, GT, EQUAL, TILDE, BANG, AT, DIESE, HASH, PERCENT, CARET, PIPE, GRAVE, QUESTION, EOF, EOL, COLON
    }

    public static class Token {

        private final TokenType type;
        private final String value;

        public Token(TokenType type, String value) {
            this.type = type;
            this.value = value;
        }

        public TokenType type() {
            return type;
        }
        
        public TokenClass tokenClass() {
            return type.tokenClass;
        }

        public String value() {
            return value;
        }
        
        @Override
        public String toString() {
            return type + "{" + value + "}";
        }

    }
    
    private class TokenizerSpliterator implements Spliterator<Token> {

        @Override
        public boolean tryAdvance(Consumer<? super Token> action) {
            if (hasMoreTokens()) {
                action.accept(nextToken());
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Spliterator<Token> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public int characteristics() {
            return ORDERED | NONNULL | IMMUTABLE;
        }
        
    }

    enum State {
        STATE_0, STATE_IDENT, STATE_EOT, /*STATE_SONT, */STATE_WHITESPACE, STATE_OP, STATE_NUM, STATE_NUM_AFTER_DOT, STATE_NUM_AFTER_E, 
        STATE_NUM_AFTER_E_SIGN, STATE_QUOTED_IDENT, STATE_QUOTED_IDENT_QUOTE, STATE_MAY_UNICODE_VARIANT_OR_IDENT, STATE_UNICODE_VARIANT,
        STATE_STRING, STATE_STRING_QUOTE, STATE_MAY_ESCAPED_STRING_OR_IDENT, STATE_MAY_BITSTRING_OR_IDENT, STATE_MAY_HEXSTRING_OR_IDENT,
        STATE_BITSTRING, STATE_HEXSTRING, STATE_MAY_PARAMETER_OR_DOLLAR_QUOTED_STRING, STATE_MAY_DOLLAR_QUOTED_STRING, STATE_DOLLAR_QUOTED_STRING,
        STATE_MAY_END_DOLLAR_QUOTED_STRING, STATE_POSITIONAL_PARAMETER,
        STATE_OP_START, STATE_OPX, STATE_MAY_OP_OR_LINE_COMMENT, STATE_MAY_OP_OR_BLOCK_COMMENT, STATE_OP_WITHOUT_FINAL_PLUS, STATE_MAY_OP_OR_FUTURE_LINE_COMMENT,
        STATE_MAY_OP_OR_FUTURE_BLOCK_COMMENT, STATE_MAY_OPX_OR_FUTURE_LINE_COMMENT, STATE_MAY_OPX_OR_FUTURE_BLOCK_COMMENT,
        STATE_LINE_COMMENT, STATE_BLOCK_COMMENT,
        STATE_MAY_BLOCK_COMMENT_LEVEL, STATE_MAY_END_BLOCK_LEVEL, STATE_MAY_CAST_OR_SLICE, STATE_MAY_NUM_OR_OP
    }
    
    enum Modifier {
        NONE, UNICODE, ESCAPED, HEX
    }
    
    private static final int NAMEDATALEN = 64-1;

    private int position = -1;
    
    private final String origin;
    private final char[] chars;
    
    private TokenType tokenType;
    private String token;

    public SqlTokenizer(String sql) {
        this.origin = sql;
        this.chars = sql.toCharArray();
    }

    public boolean hasMoreTokens() {
    	boolean has = position+1 < chars.length;
    	debug("hasMoreTokens = " + has + " because position " + position + "/" + chars.length);
        return has;
    }
    
    private void debug(String s) {
        System.out.println("[DEBUG] " + s);
    }
    
    public String nextTokenValue() {
        StringBuilder sb = new StringBuilder();
        Modifier modifier = Modifier.NONE;
        TokenType nextType = null;
        State state = State.STATE_0;
        int exitPatternPosition = 0;
        String exitPattern = "";
        int commentLevel = 0;
        int tooManyChars = 0;
        while (position <= chars.length && state != STATE_EOT) {
        	position++;
        	debug("#### state = " + state + " (" + position + "/" + chars.length + ")");
        	final char ch;
        	final CharType type;
        	if (position == chars.length) {
        		ch = '\0';
        		type = EOF;
        	} else {
        		ch = chars[position];
        		type = toCharType(ch);
        	}
            debug("char = " + type);
            
			switch (state) {
				case STATE_0:
					switch (type) {
						case LETTER:
							if (ch == 'U' || ch == 'u') {
								state = STATE_MAY_UNICODE_VARIANT_OR_IDENT;
							} else if (ch == 'E' || ch == 'e') {
								state = State.STATE_MAY_ESCAPED_STRING_OR_IDENT;
							} else if (ch == 'B' || ch == 'b') {
								state = State.STATE_MAY_BITSTRING_OR_IDENT;
							} else if (ch == 'X' || ch == 'x') {
								state = State.STATE_MAY_HEXSTRING_OR_IDENT;
							} else {
								state = STATE_IDENT;
							}
							break;
						case UNDERSCORE:
							state = STATE_IDENT;
							break;
						case WHITESPACE:
						case EOL:
							state = STATE_WHITESPACE;
							break;
						case DIGIT:
							state = STATE_NUM;
							break;
						case DOUBLE_QUOTE:
							state = STATE_QUOTED_IDENT;
							break;
						case QUOTE:
							state = STATE_STRING;
							break;
						case DOLLAR:
							state = STATE_MAY_PARAMETER_OR_DOLLAR_QUOTED_STRING;
							break;
						case PLUS:
						case ASTERISK:
						case LT:
						case GT:
						case EQUAL:
							state = STATE_OP_START;
							break;
						case TILDE:
						case BANG:
						case AT:
						case HASH:
						case PERCENT:
						case CARET:
						case AMPERSAND:
						case PIPE:
						case GRAVE:
						case QUESTION:
							state = STATE_OPX;
							break;
						case MINUS:
							state = STATE_MAY_OP_OR_LINE_COMMENT;
							break;
						case DIV:
							state = STATE_MAY_OP_OR_BLOCK_COMMENT;
							break;
						case LPAREN:
							nextType = TokenType.LPAREN;
							state = STATE_EOT;
							break;
						case RPAREN:
							nextType = TokenType.RPAREN;
							state = STATE_EOT;
							break;
						case LBRACK:
							nextType = TokenType.LBRACK;
							state = STATE_EOT;
							break;
						case RBRACK:
							nextType = TokenType.RBRACK;
							state = STATE_EOT;
							break;
						case COMMA:
							nextType = TokenType.COMMA;
							state = STATE_EOT;
							break;
						case SEMI:
							nextType = TokenType.SEMI;
							state = STATE_EOT;
							break;
						case DOT:
							state = STATE_MAY_NUM_OR_OP;
							break;
						case COLON:
							state = STATE_MAY_CAST_OR_SLICE;
							break;
						case UNKNOWN:
						default:
							parseError("Unknown character [" + ch + "] of type " + type);
					}
					break;
				case STATE_IDENT:
					switch (type) {
						case LETTER:
						case UNDERSCORE:
						case DIGIT:
						case DOLLAR:
							state = STATE_IDENT;
							break;
						default:
							nextType = TokenType.IDENTIFIER;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_WHITESPACE:
					if (type == WHITESPACE || type == EOL) {
						state = STATE_WHITESPACE;
					} else {
						nextType = TokenType.WHITESPACE;
						state = STATE_EOT;
						tooManyChars = 1;
					}
					break;
				case STATE_NUM:
					switch (type) {
						case DIGIT:
							break;
						case LETTER:
							if (ch == 'e') {
								state = STATE_NUM_AFTER_E;
							} else {
								nextType = TokenType.NUMERIC;
								state = STATE_EOT;
								tooManyChars = 1;
							}
							break;
						case DOT:
							state = STATE_NUM_AFTER_DOT;
							break;
						default:
							nextType = TokenType.NUMERIC;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_NUM_AFTER_DOT:
					switch (type) {
						case DIGIT:
							break;
						case LETTER:
							if (ch == 'e') {
								state = STATE_NUM_AFTER_E;
							} else {
								parseError();
							}
							break;
						default:
							nextType = TokenType.NUMERIC;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_NUM_AFTER_E:
					switch (type) {
						case PLUS:
						case MINUS:
							state = STATE_NUM_AFTER_E_SIGN;
							break;
						case DIGIT:
							state = STATE_NUM_AFTER_E_SIGN;
							break;
						default:
							parseError();
					}
					break;
				case STATE_NUM_AFTER_E_SIGN:
					switch (type) {
						case DIGIT:
							break;
						default:
							nextType = TokenType.NUMERIC;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_MAY_NUM_OR_OP:
					switch (type) {
						case DIGIT:
							state = STATE_NUM_AFTER_DOT;
							break;
						default:
							nextType = TokenType.DOT;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_QUOTED_IDENT:
					switch (type) {
						case DOUBLE_QUOTE:
							state = STATE_QUOTED_IDENT_QUOTE;
							break;
						case EOF:
							parseError();
						default:
					}
					break;
				case STATE_QUOTED_IDENT_QUOTE:
					switch (type) {
						case DOUBLE_QUOTE:
							state = STATE_QUOTED_IDENT;
							break;
						default:
							nextType = TokenType.QUOTED_IDENTIFIER;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_MAY_UNICODE_VARIANT_OR_IDENT:
					switch (type) {
						case AMPERSAND:
							state = STATE_UNICODE_VARIANT;
							break;
						case LETTER:
						case UNDERSCORE:
						case DIGIT:
						case DOLLAR:
							state = STATE_IDENT;
							break;
						default:
							nextType = TokenType.IDENTIFIER;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_UNICODE_VARIANT:
					if (type == CharType.DOUBLE_QUOTE) {
						modifier = Modifier.UNICODE;
						state = State.STATE_QUOTED_IDENT;
					} else if (type == CharType.QUOTE) {
						modifier = Modifier.UNICODE;
						state = State.STATE_STRING;
					} else {
						nextType = TokenType.IDENTIFIER;
						state = STATE_EOT;
						tooManyChars = 2;
					}
					break;
				case STATE_STRING:
					switch (type) {
						case QUOTE:
							state = STATE_STRING_QUOTE;
							break;
						case EOF:
							parseError();
						default:
					}
					break;
				case STATE_STRING_QUOTE:
					switch (type) {
						case QUOTE:
							state = STATE_STRING;
							break;
						default:
							nextType = TokenType.STRING;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_MAY_ESCAPED_STRING_OR_IDENT:
					switch (type) {
						case QUOTE:
							modifier = Modifier.ESCAPED;
							state = STATE_STRING;
							break;
						case LETTER:
						case UNDERSCORE:
						case DIGIT:
						case DOLLAR:
							state = STATE_IDENT;
							break;
						default:
							nextType = TokenType.IDENTIFIER;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_MAY_BITSTRING_OR_IDENT:
					switch (type) {
						case QUOTE:
							state = STATE_BITSTRING;
							break;
						case LETTER:
						case UNDERSCORE:
						case DIGIT:
						case DOLLAR:
							state = STATE_IDENT;
							break;
						default:
							nextType = TokenType.IDENTIFIER;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_MAY_HEXSTRING_OR_IDENT:
					switch (type) {
						case QUOTE:
							state = STATE_HEXSTRING;
							break;
						case LETTER:
						case UNDERSCORE:
						case DIGIT:
						case DOLLAR:
							state = STATE_IDENT;
							break;
						default:
							nextType = TokenType.IDENTIFIER;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_BITSTRING:
					switch (type) {
						case QUOTE:
							state = STATE_EOT;
							nextType = TokenType.BITSTRING;
							break;
						default:
							if (ch != '0' && ch != '1') {
								parseError("Bitstring can only contains 0 or 1 characters");
							}
					}
					break;
				case STATE_HEXSTRING:
					switch (type) {
						case QUOTE:
							state = STATE_EOT;
							nextType = TokenType.HEXSTRING;
							break;
						default:
							if (!((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F') || (ch >= 'a' && ch <= 'f'))) {
								parseError("Hexstring can only contains hexadecimal characters (0123456789ABCDEF)");
							}
					}
					break;
				case STATE_MAY_PARAMETER_OR_DOLLAR_QUOTED_STRING:
					switch (type) {
						case DOLLAR:
							state = STATE_DOLLAR_QUOTED_STRING;
							exitPattern = "";
							debug("exitPattern empty");
							break;
						case DIGIT:
							state = STATE_POSITIONAL_PARAMETER;
							break;
						case UNDERSCORE:
						case LETTER:
							state = STATE_MAY_DOLLAR_QUOTED_STRING;
							break;
						default:
							parseError("syntax error at or near [$]");
					}
					break;
				case STATE_DOLLAR_QUOTED_STRING:
					if (type == DOLLAR) {
						state = STATE_MAY_END_DOLLAR_QUOTED_STRING;
						exitPatternPosition = 0;
					} else if (type == EOF) {
						parseError("unterminated dollar-quoted string at or near [" + sb.toString() + "]");
					}
					break;
				case STATE_MAY_END_DOLLAR_QUOTED_STRING:
					if (type == DOLLAR) {
						if (exitPatternPosition == exitPattern.length()) {
							state = STATE_EOT;
							nextType = TokenType.DOLLAR_QUOTED_STRING;
						} else {
							state = STATE_DOLLAR_QUOTED_STRING;
						}
					} else if (type == EOF) {
						parseError("unterminated dollar-quoted string at or near [" + sb.toString() + "]");
					} else {
						if (exitPattern.charAt(exitPatternPosition++) != ch) {
							state = STATE_DOLLAR_QUOTED_STRING;
						}
					}
					break;
				case STATE_MAY_DOLLAR_QUOTED_STRING:
					switch (type) {
						case LETTER:
						case DIGIT:
						case UNDERSCORE:
							break;
						case DOLLAR:
							state = STATE_DOLLAR_QUOTED_STRING;
							exitPattern = sb.toString().substring(1);
							debug("exitPattern " + exitPattern);
							break;
						default:
							parseError("syntax error at or near [$]");
					}
					break;
				case STATE_POSITIONAL_PARAMETER:
					if (type != DIGIT) {
						nextType = TokenType.POSITIONAL_PARAMETER;
						state = STATE_EOT;
						tooManyChars = 1;
					}
					break;
				case STATE_OP_START:
					switch (type) {
						case PLUS:
							state = STATE_OP_WITHOUT_FINAL_PLUS;
							break;
						case ASTERISK:
						case LT:
						case GT:
						case EQUAL:
							state = STATE_OP;
							break;
						case TILDE:
						case BANG:
						case AT:
						case HASH:
						case PERCENT:
						case CARET:
						case AMPERSAND:
						case PIPE:
						case GRAVE:
						case QUESTION:
							state = STATE_OPX;
							break;
						case MINUS:
							state = STATE_MAY_OP_OR_FUTURE_LINE_COMMENT;
							break;
						case DIV:
							state = STATE_MAY_OP_OR_FUTURE_BLOCK_COMMENT;
							break;
						case EOF:
						default:
							nextType = TokenType.OPERATOR;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_OP:
					switch (type) {
						case PLUS:
							state = STATE_OP_WITHOUT_FINAL_PLUS;
							break;
						case ASTERISK:
						case LT:
						case GT:
						case EQUAL:
							state = STATE_OP;
							break;
						case TILDE:
						case BANG:
						case AT:
						case HASH:
						case PERCENT:
						case CARET:
						case AMPERSAND:
						case PIPE:
						case GRAVE:
						case QUESTION:
							state = STATE_OPX;
							break;
						case MINUS:
							state = STATE_MAY_OP_OR_FUTURE_LINE_COMMENT;
							break;
						case DIV:
							state = STATE_MAY_OP_OR_FUTURE_BLOCK_COMMENT;
							break;
						case EOF:
						default:
							nextType = TokenType.OPERATOR;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_OPX:
					switch (type) {
						case PLUS:
						case ASTERISK:
						case LT:
						case GT:
						case EQUAL:
						case TILDE:
						case BANG:
						case AT:
						case HASH:
						case PERCENT:
						case CARET:
						case AMPERSAND:
						case PIPE:
						case GRAVE:
						case QUESTION:
							state = STATE_OPX;
							break;
						case MINUS:
							state = STATE_MAY_OPX_OR_FUTURE_LINE_COMMENT;
							break;
						case DIV:
							state = STATE_MAY_OPX_OR_FUTURE_BLOCK_COMMENT;
							break;
						case EOF:
						default:
							nextType = TokenType.OPERATOR;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_OP_WITHOUT_FINAL_PLUS:
					switch (type) {
						case PLUS:
							state = STATE_OP_WITHOUT_FINAL_PLUS;
							break;
						case ASTERISK:
						case LT:
						case GT:
						case EQUAL:
							state = STATE_OP;
							break;
						case TILDE:
						case BANG:
						case AT:
						case HASH:
						case PERCENT:
						case CARET:
						case AMPERSAND:
						case PIPE:
						case GRAVE:
						case QUESTION:
							state = STATE_OPX;
							break;
						case MINUS:
							state = STATE_MAY_OP_OR_FUTURE_LINE_COMMENT;
							break;
						case DIV:
							state = STATE_MAY_OP_OR_FUTURE_BLOCK_COMMENT;
							break;
						case EOF:
						default:
							parseError("Cannot end and operator with [+] when there is no [~ ! @ # % ^ & | ` ?] before");
					}
					break;
				case STATE_MAY_OP_OR_FUTURE_LINE_COMMENT:
					switch (type) {
						case PLUS:
							state = STATE_OP_WITHOUT_FINAL_PLUS;
							break;
						case ASTERISK:
						case LT:
						case GT:
						case EQUAL:
							state = STATE_OP;
							break;
						case TILDE:
						case BANG:
						case AT:
						case HASH:
						case PERCENT:
						case CARET:
						case AMPERSAND:
						case PIPE:
						case GRAVE:
						case QUESTION:
							state = STATE_OPX;
							break;
						case MINUS:
							nextType = TokenType.OPERATOR;
							state = STATE_EOT;
							tooManyChars = 2;
							break;
						case DIV:
							state = STATE_MAY_OP_OR_FUTURE_BLOCK_COMMENT;
							break;
						case EOF:
						default:
							parseError("Cannot end and operator with [+] when there is no [~ ! @ # % ^ & | ` ?] before");
					}
					break;
				case STATE_MAY_OP_OR_FUTURE_BLOCK_COMMENT:
					switch (type) {
						case PLUS:
							state = STATE_OP_WITHOUT_FINAL_PLUS;
							break;
						case ASTERISK:
							nextType = TokenType.OPERATOR;
							state = STATE_EOT;
							tooManyChars = 2;
							break;
						case LT:
						case GT:
						case EQUAL:
							state = STATE_OP;
							break;
						case TILDE:
						case BANG:
						case AT:
						case HASH:
						case PERCENT:
						case CARET:
						case AMPERSAND:
						case PIPE:
						case GRAVE:
						case QUESTION:
							state = STATE_OPX;
							break;
						case MINUS:
							state = STATE_MAY_OP_OR_FUTURE_LINE_COMMENT;
							break;
						case DIV:
							state = STATE_MAY_OP_OR_FUTURE_BLOCK_COMMENT;
							break;
						case EOF:
							nextType = TokenType.OPERATOR;
							state = STATE_EOT;
							tooManyChars = 1;
							break;
						default:
							parseError("Cannot end and operator with [+] when there is no [~ ! @ # % ^ & | ` ?] before");
					}
					break;
				case STATE_MAY_OPX_OR_FUTURE_LINE_COMMENT:
					switch (type) {
						case PLUS:
						case ASTERISK:
						case LT:
						case GT:
						case EQUAL:
						case TILDE:
						case BANG:
						case AT:
						case HASH:
						case PERCENT:
						case CARET:
						case AMPERSAND:
						case PIPE:
						case GRAVE:
						case QUESTION:
							state = STATE_OPX;
							break;
						case MINUS:
							nextType = TokenType.OPERATOR;
							state = STATE_EOT;
							tooManyChars = 2;
							break;
						case DIV:
							state = STATE_MAY_OPX_OR_FUTURE_BLOCK_COMMENT;
							break;
						case EOF:
						default:
							nextType = TokenType.OPERATOR;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_MAY_OPX_OR_FUTURE_BLOCK_COMMENT:
					switch (type) {
						case PLUS:
						case ASTERISK:
							nextType = TokenType.OPERATOR;
							state = STATE_EOT;
							tooManyChars = 2;
							break;
						case LT:
						case GT:
						case EQUAL:
						case TILDE:
						case BANG:
						case AT:
						case HASH:
						case PERCENT:
						case CARET:
						case AMPERSAND:
						case PIPE:
						case GRAVE:
						case QUESTION:
							state = STATE_OPX;
							break;
						case MINUS:
							state = STATE_MAY_OPX_OR_FUTURE_LINE_COMMENT;
							break;
						case DIV:
							state = STATE_OPX;
							break;
						case EOF:
							nextType = TokenType.OPERATOR;
							state = STATE_EOT;
							tooManyChars = 1;
							break;
						default:
							parseError("Cannot end and operator with [+] when there is no [~ ! @ # % ^ & | ` ?] before");
					}
					break;
				case STATE_MAY_OP_OR_LINE_COMMENT:
					switch (type) {
						case PLUS:
							state = STATE_OP_WITHOUT_FINAL_PLUS;
							break;
						case ASTERISK:
						case LT:
						case GT:
						case EQUAL:
							state = STATE_OP;
							break;
						case TILDE:
						case BANG:
						case AT:
						case HASH:
						case PERCENT:
						case CARET:
						case AMPERSAND:
						case PIPE:
						case GRAVE:
						case QUESTION:
							state = STATE_OPX;
							break;
						case MINUS:
							state = STATE_LINE_COMMENT;
							break;
						case DIV:
							state = STATE_MAY_OP_OR_FUTURE_BLOCK_COMMENT;
							break;
						case EOF:
						default:
							nextType = TokenType.OPERATOR;
							state = STATE_EOT;
							tooManyChars = 1;
					}
					break;
				case STATE_MAY_OP_OR_BLOCK_COMMENT:
					if (type == ASTERISK) {
						state = STATE_BLOCK_COMMENT;
					} else {
						state = STATE_OP;
					}
					break;
				case STATE_LINE_COMMENT:

					if (type == EOL || type == EOF) {
						nextType = TokenType.LINE_COMMENT;
						state = STATE_EOT;
						tooManyChars = 1;
					}
					break;
				case STATE_BLOCK_COMMENT:
					if (type == DIV) {
						state = STATE_MAY_BLOCK_COMMENT_LEVEL;
					} else if (type == ASTERISK) {
						state = STATE_MAY_END_BLOCK_LEVEL;
					} else if (type == EOF) {
						parseError("unterminated comment at or near");
					}
					break;
				case STATE_MAY_END_BLOCK_LEVEL:
					if (type == DIV) {
						if (commentLevel > 0) {
							commentLevel--;
						} else {
							nextType = TokenType.BLOCK_COMMENT;
							state = STATE_EOT;
						}
					} else if (type == EOF) {
						parseError("unterminated comment at or near");
					} else {
						state = STATE_BLOCK_COMMENT;
					}
					break;
				case STATE_MAY_BLOCK_COMMENT_LEVEL:
					if (type == ASTERISK) {
						commentLevel++;
						state = STATE_BLOCK_COMMENT;
					} else if (type == EOF) {
						parseError("unterminated comment at or near");
					} else {
						state = STATE_BLOCK_COMMENT;
					}
					break;
				case STATE_MAY_CAST_OR_SLICE:
					if (type == COLON) {
						nextType = TokenType.OPERATOR;
						state = STATE_EOT;
					} else {
						nextType = TokenType.OPERATOR;
						state = STATE_EOT;
						tooManyChars = 1;
					}
					break;
				default:
					parseError("Unknown state " + state);
			}
			
			sb.append(ch);
            debug("state => " + state);
            debug("text => " + sb.toString());
        }
        if (tooManyChars > 0) {
        	debug("tooManyChars = " + tooManyChars);
            sb.delete(sb.length() - tooManyChars, sb.length());
            position -= tooManyChars;
        }
        token = sb.toString();
        tokenType = modify(nextType, modifier);
        debug("==> return [" + token + "] of type " + nextType + " position is " + position + "/" + chars.length);
        return token;
    }
    
    private TokenType modify(TokenType type, Modifier modifier) {
        TokenType result;
        if (modifier == Modifier.NONE) {
            result = type;
        } else {
            if (type == TokenType.STRING) {
                if (modifier == Modifier.ESCAPED) {
                    result = TokenType.ESCAPED_STRING;
                } else if (modifier == Modifier.UNICODE) {
                    result = TokenType.UNICODE_STRING;
                } else {
                    result = null;
                }
            } else if (type == TokenType.QUOTED_IDENTIFIER) {
                if (modifier == Modifier.UNICODE) {
                    result = TokenType.UNICODE_QUOTED_IDENTIFIER;
                } else {
                    result = null;
                }
            } else {
                result = type;
            }
        }
        if (result == null) {
            throw new IllegalStateException("The modifier " + modifier + " is not compatible with type " + type);
        }
        return result;
    }
    
    public Token nextToken() {
        nextTokenValue();
        return new Token(tokenType, token);
    }

    public TokenType getCurrentTokenType() {
        return tokenType;
    }

    public Stream<Token> stream() {
        return StreamSupport.stream(new TokenizerSpliterator(), false);
    }
    
    private void parseError() {
        throw new SqlParseException("", position - 1, origin);
    }

    private void parseError(String msg) {
        throw new SqlParseException(msg, position - 1, origin);
    }
    
    private static boolean isSpace(char ch) {
        return ch == ' ' || ch == '\t';
    }

    private static boolean isLetter(char ch) {
        return Character.isLetter(ch);
    }

    private static CharType toCharType(char ch) {
        CharType result;
        if (isSpace(ch)) {
            result = CharType.WHITESPACE;
        } else if (isLetter(ch)) {
            result = LETTER;
        } else if (Character.isDigit(ch)) {
            result = DIGIT;
        } else {
            switch (ch) {
            case '$':
                result = DOLLAR;
                break;
            case '_':
                result = UNDERSCORE;
                break;
            case '"':
                result = DOUBLE_QUOTE;
                break;
            case '\'':
                result = QUOTE;
                break;
            case '=':
                result = EQUAL;
                break;
            case '+':
                result = PLUS;
                break;
            case '-':
                result = MINUS;
                break;
            case '/':
                result = DIV;
                break;
            case '*':
                result = ASTERISK;
                break;
            case '<':
                result = LT;
                break;
            case '>':
                result = GT;
                break;
            case '~':
                result = TILDE;
                break;
            case '!':
                result = BANG;
                break;
            case '@':
                result = AT;
                break;
            case '#':
                result = HASH;
                break;
            case '%':
                result = PERCENT;
                break;
            case '^':
                result = CARET;
                break;
            case '|':
                result = PIPE;
                break;
            case '`':
                result = GRAVE;
                break;
            case '?':
                result = QUESTION;
                break;
            case '.':
                result = DOT;
                break;
            case '\0':
                result = EOF;
                break;
            case '&':
                result = AMPERSAND;
                break;
            case '\n':
                result = EOL;
                break;
            case '(':
                result = CharType.LPAREN;
                break;
            case ')':
                result = CharType.RPAREN;
                break;
            case '[':
                result = CharType.LBRACK;
                break;
            case ']':
                result = CharType.RBRACK;
                break;
            case ',':
                result = CharType.COMMA;
                break;
            case ';':
                result = CharType.SEMI;
                break;
            case ':':
                result = CharType.COLON;
                break;
            default:
                result = UNKNOWN;
            }
        }
        return result;
    }
    
}
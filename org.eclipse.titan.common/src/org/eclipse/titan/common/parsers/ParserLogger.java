/******************************************************************************
 * Copyright (c) 2000-2021 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.common.parsers;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.eclipse.titan.common.parsers.cfg.CfgInterval;

/**
 * USED FOR LOGGING PURPOSES.<br>
 * Logger utility to print parse tree and interval tree.
 * @author Arpad Lovassy
 */
public final class ParserLogger {
	/** date format in ISO 8601 */
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private static IPrinter sPrinter = new ConsolePrinter();

	private ParserLogger() {
		// intentionally private to disable instantiation
	}

	private static void setPrinter( final IPrinter aPrinter ) {
		sPrinter = aPrinter;
	}

	/**
	 * Logs a parse tree. (General version)
	 * Token name is resolved if ...LexerLogUtil.java is generated,
	 * otherwise only token type index is displayed
	 * @param aRoot parse tree
	 * @param aParser parser to get rule names
	 * @param aTokenNameResolver resolver to get token name
	 * @param aPrinter printer
	 * @param aDescription description of the parsing type, for the logging (for example: TTCN-3, Cfg, ASN.1)
	 */
	public static synchronized void log( final ParseTree aRoot,
			final Parser aParser,
			final TokenNameResolver aTokenNameResolver,
			final IPrinter aPrinter,
			final String aDescription ) {
		if ( !aParser.getBuildParseTree() ) {
			// Parse tree logging is not requested
			return;
		}
		setPrinter( aPrinter );
		aPrinter.println( "---- parse tree" );
		aPrinter.println( DATE_FORMAT.format( new Date() ) ) ;
		aPrinter.println( "Parser type: " + aDescription + ", Prediction mode: " + aParser.getInterpreter().getPredictionMode() );
		aPrinter.println( "Call stack: " + printCallStackReverse( 4 ) );
		final TokenStream tokenStream = aParser.getTokenStream();
		if ( ! ( tokenStream instanceof CommonTokenStream ) ) {
			aPrinter.println("ERROR: tokenStream is not CommonTokenStream");
			return;
		}
		final CommonTokenStream commonTokenStream = (CommonTokenStream)tokenStream;
		final List<Token> tokens = commonTokenStream.getTokens();
		log( aRoot, aParser, tokens, aTokenNameResolver );
	}

	/**
	 * Logs a parse tree
	 * @param aRoot parse tree
	 * @param aParser parser to get rule names
	 * @param aTokens token list to get tokens by index (for getting tokens of a rule)
	 * @param aLexerLogUtil resolver to get token name by method 1, see TokenNameResolver
	 */
	public static void log( final ParseTree aRoot,
			final Parser aParser,
			final List<Token> aTokens,
			final ILexerLogUtil aLexerLogUtil ) {
		log( aRoot, aParser, aTokens, new TokenNameResolver( aLexerLogUtil ) );
	}

	/**
	 * Logs a parse tree
	 * @param aRoot parse tree
	 * @param aParser parser to get rule names
	 * @param aTokens token list to get tokens by index (for getting tokens of a rule)
	 * @param aLexer resolver to get token name by method 2, see TokenNameResolver
	 */
	public static void log( final ParseTree aRoot,
			final Parser aParser,
			final List<Token> aTokens,
			final Lexer aLexer ) {
		log( aRoot, aParser, aTokens, new TokenNameResolver( aLexer ) );
	}

	/**
	 * Logs a parse tree, token name is not resolved, only token type index is displayed
	 * @param aRoot parse tree
	 * @param aParser parser to get rule names
	 * @param aTokens token list to get tokens by index (for getting tokens of a rule)
	 */
	public static void log( final ParseTree aRoot,
			final Parser aParser,
			final List<Token> aTokens ) {
		log( aRoot, aParser, aTokens, new TokenNameResolver() );
	}

	/**
	 * Logs a parse tree
	 * @param aRoot parse tree
	 * @param aParser parser to get rule names
	 * @param aTokens token list to get tokens by index (for getting tokens of a rule)
	 * @param aTokenNameResolver resolver to get token name
	 */
	private static void log( final ParseTree aRoot,
			final Parser aParser,
			final List<Token> aTokens,
			final TokenNameResolver aTokenNameResolver ) {
		try {
			log( aRoot, aParser, aTokens, aTokenNameResolver, new ArrayList<Boolean>(), false, false );
		} catch( Exception e ) {
			final StringWriter sw = new StringWriter();
			final PrintWriter pw = new PrintWriter( sw );
			e.printStackTrace( pw );
			println( sw.toString() ); // stack trace as a string
		}
	}

	/**
	 * Logs a parse tree.
	 * Internal version.
	 * RECURSIVE
	 * @param aRoot parse tree
	 * @param aParser parser to get rule name
	 * @param aTokens token list to get tokens by index (for getting tokens of a rule)
	 * @param aTokenNameResolver resolver to get token name
	 * @param aLevel a list, that tells, if tree section is drawn for that level (parent).
	 *               If the parent of the given level is already the last child, tree sections are not drawn below.
	 *               NOTE: indentation level is aLevel.size()
	 * @param aParentOneChild parent has 1 child
	 * @param aLastChild this node is the last child of its parent
	 */
	private static void log( final ParseTree aRoot,
			final Parser aParser,
			final List<Token> aTokens,
			final TokenNameResolver aTokenNameResolver,
			final List<Boolean> aLevel,
			final boolean aParentOneChild,
			final boolean aLastChild ) {
		if ( aRoot == null ) {
			println( "ERROR: ParseTree root is null" );
			return;
		}
		if ( !aParser.getBuildParseTree() ) {
			println( "ERROR: ParseTree is not build. Call Parser.setBuildParseTree( true ); BEFORE parsing. Or do NOT call Parser.setBuildParseTree( false );" );
			return;
		}

		if ( aRoot instanceof ParserRuleContext ) {
			final ParserRuleContext rule = (ParserRuleContext)aRoot;
			final String ruleInfo = getRuleInfo( rule, aParser, aTokenNameResolver );
			if ( aParentOneChild ) {
				printArrow( ruleInfo );
			} else {
				printIndent( ruleInfo, aLevel );
			}

			final int count = rule.getChildCount();
			final boolean oneChild = count == 1 && rule.exception == null;
			if ( !oneChild ) {
				println( ": '" + getEscapedRuleText( rule, aTokens ) + "'" + getExceptionInfo( rule, aTokenNameResolver ) );
			}

			for( int i = 0; i < count; i++ ) {
				final ParseTree child = rule.getChild( i );
				List<Boolean> level = aLevel;
				if ( !aParentOneChild ) {
					level = new ArrayList<Boolean>( aLevel );
					if ( aLastChild ) {
						level.set( level.size() - 1, false );
					}
					level.add( true );
				}
				log( child, aParser, aTokens, aTokenNameResolver, level, oneChild, i == count - 1 );
			}
		} else if ( aRoot instanceof TerminalNodeImpl ) {
			final TerminalNodeImpl tn = (TerminalNodeImpl)aRoot;
			if ( aParentOneChild ) {
				println( ": '" + getEscapedTokenText( tn.getSymbol() ) + "'" );
			}

			printIndent( getTokenInfo( tn.getSymbol(), aTokenNameResolver ), aLevel );
			if ( tn.parent == null ) {
				print(", parent == null <-------------------------------------------------------------- ERROR");
			}
			println();
		} else if ( aRoot instanceof AddedParseTree ) {
			final AddedParseTree apt = (AddedParseTree)aRoot;
			if ( aParentOneChild ) {
				println( ": '" + getEscapedText( apt.getText() ) + "'" );
			}
			printIndent( "AddedParseString: " + getEscapedText( apt.getText() ), aLevel );
			if ( apt.getParent() == null ) {
				print(", parent == null <-------------------------------------------------------------- ERROR");
			}
			println();
		} else {
			println( "ERROR: INVALID ParseTree type" );
		}
	}

	/**
	 * Rule exception info in string format for logging purpose
	 * @param aRule rule
	 * @param aTokenNameResolver resolver to get token name
	 * @return exception stack trace + some other info from the exception object
	 */
	private static String getExceptionInfo( final ParserRuleContext aRule, final TokenNameResolver aTokenNameResolver ) {
		final RecognitionException e = aRule.exception;
		if ( e == null ) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		sb.append(", ERROR: mismatch, expected tokens: [" );
		final List<Integer> expectedTokens = e.getExpectedTokens().toList();
		for ( int i = 0; i < expectedTokens.size(); i++ ) {
			if ( i > 0 ) {
				sb.append(", ");
			}
			final int tokenType = expectedTokens.get( i );
			sb.append( getTokenName( tokenType, aTokenNameResolver ) );
		}
		sb.append(']');
		if ( e instanceof NoViableAltException ) {
			final NoViableAltException nvae = (NoViableAltException)e;
			sb.append( ", start token: " + getTokenInfo( nvae.getStartToken(), aTokenNameResolver ) );
		}

		return sb.toString();
	}

	/**
	 * Rule info in string format for logging purpose
	 * @param aRule rule
	 * @param aParser parser to get rule name
	 * @param aTokenNameResolver resolver to get token name (used only if rule is erroneous)
	 * @return rule name and exception and some other info if rule is erroneous
	 */
	private static String getRuleInfo( final ParserRuleContext aRule, final Parser aParser, final TokenNameResolver aTokenNameResolver ) {
		// only rule name
		final String info = aParser.getRuleInvocationStack( aRule ).get( 0 );
		return info;
	}

	/**
	 * Escaped rule text including hidden tokens
	 * For logging purposes
	 * @param aRule rule
	 * @param aTokens token list to get the tokens (all, hidden and not hidden also) from
	 * @return escaped rule text
	 */
	private static String getEscapedRuleText( final ParserRuleContext aRule,
			final List<Token> aTokens ) {
		final Token startToken = aRule.start;
		if ( startToken == null ) {
			println( "ERROR: ParseLogger.getEscapedRuleText() startToken == null" );
			return "";
		}

		final Token stopToken = aRule.stop;
		if ( stopToken == null ) {
			println( "ERROR: ParseLogger.getEscapedRuleText() stopToken == null" );
			return "";
		}

		final int startIndex = startToken.getTokenIndex();
		final int stopIndex = stopToken.getTokenIndex();
		final StringBuilder sb = new StringBuilder();
		for ( int i = startIndex; i <= stopIndex; i++ ) {
			try {
				sb.append( getEscapedTokenText( aTokens.get( i ) ) );
			} catch ( IndexOutOfBoundsException e ) {
				sb.append('_');
			}
		}
		return sb.toString();
	}

	/**
	 * Rule text including hidden tokens
	 * For logging purposes
	 * @param aRule rule
	 * @param aTokens token list to get the tokens (all, hidden and not hidden also) from
	 * @return rule text including hidden tokens. First and last tokens are non-hidden.
	 */
	public static String getRuleText( final ParserRuleContext aRule,
			final List<Token> aTokens ) {
		final Token startToken = aRule.start;
		if ( startToken == null ) {
			println( "ERROR: ParseLogger.getEscapedRuleText() startToken == null" );
			return "";
		}

		final Token stopToken = aRule.stop;
		if ( stopToken == null ) {
			println( "ERROR: ParseLogger.getEscapedRuleText() stopToken == null" );
			return "";
		}

		final int startIndex = startToken.getTokenIndex();
		final int stopIndex = stopToken.getTokenIndex();
		final StringBuilder sb = new StringBuilder();
		for ( int i = startIndex; i <= stopIndex; i++ ) {
			sb.append( aTokens.get( i ) );
		}
		return sb.toString();
	}

	/**
	 * Token info in string format for logging purpose
	 * @param aToken token
	 * @param aTokenNameResolver resolver to get token name
	 * @return &lt;token name&gt;: '&lt;token text&gt;', @&lt;token index&gt;, &lt;line&gt;:&lt;column&gt;[, channel=&lt;channel&gt;]
	 *         <br>where
	 *         <br>&lt;token index&gt; starts  from 0,
	 *         <br>&lt;line&gt; starts from 1,
	 *         <br>&lt;column&gt; starts from 0,
	 *         <br>channel info is provided if &lt;channel&gt; > 0 (hidden channel)
	 */
	private static String getTokenInfo( final Token aToken, final TokenNameResolver aTokenNameResolver ) {
		final StringBuilder sb = new StringBuilder();
		final int tokenType = aToken.getType();
		final String tokenName = getTokenName( tokenType, aTokenNameResolver );
		sb.append( tokenName );
		sb.append( ": " );

		sb.append( '\'' );
		sb.append( getEscapedTokenText( aToken ) );
		sb.append( '\'' );

		sb.append( ", @" + aToken.getTokenIndex() );
		sb.append( ", " + aToken.getLine() + ":" + aToken.getCharPositionInLine() );
		if ( aToken.getChannel() > 0 ) {
			sb.append( ", channel=" );
			sb.append( aToken.getChannel() );
		}
		return sb.toString();
	}

	/**
	 * Gets escaped token text
	 * Escaped chars are converted to printable strings.
	 * @param aToken input token
	 * @return escaped token text
	 */
	private static String getEscapedTokenText( final Token aToken ) {
		return getEscapedText( aToken.getText() );
	}

	/**
	 * Gets escaped text
	 * Escaped chars are converted to printable strings.
	 * @param aText input text
	 * @return escaped text
	 */
	public static String getEscapedText( final String aText ) {
		String txt = aText;
		if ( txt == null ) {
			return "";
		}
		txt = txt.replace( "\n", "\\n" );
		txt = txt.replace( "\r", "\\r" );
		txt = txt.replace( "\t", "\\t" );
		return txt;
	}

	/**
	 * @param aTokenType token type index
	 * @param aTokenNameResolver token name resolver
	 * @return resolved token name
	 */
	private static String getTokenName( final int aTokenType, TokenNameResolver aTokenNameResolver ) {
		/*
		TODO: use Vocabulary interface when ANTLR 4.5 is used
		See https://github.com/antlr/antlr4/pull/712

		Vocabulary vocabulary = recognizer.getVocabulary();
		final String symbolicName = vocabulary.getSymbolicName( aTokenType );
		return symbolicName;
		 */
		if ( aTokenNameResolver == null ) {
			aTokenNameResolver = new TokenNameResolver();
		}
		return aTokenNameResolver.getTokenName( aTokenType );
	}

	/**
	 * Logs an interval tree.
	 * @param aRootInterval the root of the interval tree
	 */
	public static void logInterval( final Interval aRootInterval ) {
		logInterval( aRootInterval, 0 );
	}

	/**
	 * Logs an interval tree.
	 * Internal version.
	 * RECURSIVE
	 * @param aRootInterval the root of the interval tree
	 * @param aLevel indentation level
	 */
	private static void logInterval( final Interval aRootInterval, final int aLevel ) {
		// root interval info
		final StringBuilder sb = new StringBuilder();
		sb.append( "" + aRootInterval.getDepth() );
		sb.append( ", " + aRootInterval.getStartOffset() );
		sb.append( ", " + aRootInterval.getStartLine() );
		sb.append( ", " + aRootInterval.getEndOffset() );
		sb.append( ", " + aRootInterval.getEndLine() );
		sb.append( ", " + aRootInterval.getType() );
		if ( aRootInterval instanceof CfgInterval ) {
			sb.append( ", " + ( ( CfgInterval )aRootInterval).getSectionType() );
		}
		if ( aRootInterval.getErroneous() ) {
			sb.append( ", ERRONEOUS" );
		}
		printIndent( sb.toString(), aLevel );
		println();
		if ( aRootInterval.getSubIntervals() != null ) {
			for ( final Interval interval : aRootInterval.getSubIntervals() ) {
				logInterval( interval, aLevel + 1 );
			}
		}
	}

	/**
	 * -> aMsg
	 * @param aMsg
	 */
	private static void printArrow( final String aMsg ) {
		print( " -> " );
		print( aMsg );
	}

	/**
	 * Prints message to console for logging purpose
	 * with level dependent indentation
	 * @param aMsg message
	 * @param aLevel indentation level
	 */
	private static void printIndent( final String aMsg, final int aLevel ) {
		final StringBuilder sb = new StringBuilder();
		for ( int i = 0; i < aLevel; i++ ) {
			sb.append( "  " );
		}
		print( sb.toString() );
		print( aMsg );
	}

	/**
	 * Prints message to console for logging purpose
	 * with level dependent indentation
	 * and also with tree structure, so a rectangular line is drawn between the parent and the children.
	 * @param aMsg message
	 * @param aLevel a list, that tells, if tree section is drawn for that level (parent).
	 *               If the parent of the given level is already the last child, tree sections are not drawn below.
	 *               NOTE: indentation level is aLevel.size()
	 */
	private static void printIndent( final String aMsg, final List<Boolean> aLevel ) {
		final StringBuilder sb = new StringBuilder();
		final int size = aLevel.size();
		for ( int i = 0; i < size; i++ ) {
			if ( i < size - 1 ) {
				sb.append( aLevel.get( i ) ? "| " : "  " );
			} else {
				sb.append( "+-" );
			}
		}
		print( sb.toString() );
		print( aMsg );
	}

	/**
	 * Prints full stack trace in one line.
	 * Short class name is used without full qualification.
	 * @param aN [in] number of methods, which are not needed. These are the top of the call stack
	 *                getStackTrace()
	 *                this function
	 *                logParseTree() function(s)
	 * @return string representation of the call stack
	 */
	public static String printCallStack( final int aN ) {
		final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		final StringBuilder sb = new StringBuilder();
		final int first = stackTrace.length - 1;
		for ( int i = first; i >= aN; i-- ) {
			final StackTraceElement ste = stackTrace[ i ];
			if ( i < first ) {
				sb.append(" -> ");
			}
			printStackTraceElement( sb, ste );
		}
		return sb.toString();
	}

	/**
	 * Prints full stack trace in one line in reversed order.
	 * Short class name is used without full qualification.
	 * @param aN [in] number of methods, which are not needed. These are the top of the call stack
	 *                getStackTrace()
	 *                this function
	 *                logParseTree() function(s)
	 * @return string representation of the call stack
	 */
	public static String printCallStackReverse( final int aN ) {
		final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		final StringBuilder sb = new StringBuilder();
		for ( int i = aN; i < stackTrace.length; i++ ) {
			final StackTraceElement ste = stackTrace[ i ];
			if ( i > aN ) {
				sb.append(" <- ");
			}
			printStackTraceElement( sb, ste );
		}
		return sb.toString();
	}

	/**
	 * Prints a stack trace element (method call of a call chain) with short class name and method name
	 * @param aSb [in/out] the log string, where new strings are added
	 * @param aSte stack trace element
	 * @return string representation of the stack trace element
	 */
	private static void printStackTraceElement( final StringBuilder aSb, final StackTraceElement aSte ) {
		final String className = aSte.getClassName();
		final String shortClassName = className.substring( className.lastIndexOf('.') + 1 );
		aSb.append( shortClassName );
		aSb.append( '.' );
		aSb.append( aSte.getMethodName() );
		aSb.append( ':' );
		aSb.append( aSte.getLineNumber() );
	}

	private static void print( final String aMsg ) {
		sPrinter.print( aMsg );
	}

	private static void println() {
		sPrinter.println();
	}

	private static void println( final String aMsg ) {
		sPrinter.println( aMsg );
	}
}

#!/bin/bash
###############################################################################
# Copyright (c) 2000-2021 Ericsson Telecom AB
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v2.0
# which accompanies this distribution, and is available at
# https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
#
# Contributors:
#   Lovassy, Arpad
#
###############################################################################

###############################################################################
# Script to compile all the .g4 files in the Titan EclipsePlug-ins repo.
#
# Example usage:
#   cd <titan.EclipsePlug-ins project root>
#   Tools/antlr4_compile.sh
###############################################################################
set -e
set -o pipefail

ANTLR_VERSION=4.3

while :
do
        case $1 in
            -av)
                ANTLR_VERSION=$2
                shift
                ;;
            *)
                break;
        esac
        shift
    done

ANTLR4="java -classpath $HOME/lib/antlr-${ANTLR_VERSION}-complete.jar org.antlr.v4.Tool"

echo Using antlr version $ANTLR_VERSION

# script directory
# http://stackoverflow.com/questions/59895/can-a-bash-script-tell-which-directory-it-is-stored-in
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

WORKSPACE_PATH=$DIR/..

# Titan Runtime
cd $WORKSPACE_PATH/org.eclipse.titan.runtime/src/org/eclipse/titan/runtime/core/cfgparser/
$ANTLR4 RuntimeCfgLexer.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.runtime.core.cfgparser
$ANTLR4 RuntimeCfgParser.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.runtime.core.cfgparser
$ANTLR4 RuntimeCfgPreParser.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.runtime.core.cfgparser

# Titan Common
cd $WORKSPACE_PATH/org.eclipse.titan.common/src/org/eclipse/titan/common/parsers/cfg/
$ANTLR4 CfgLexer.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.common.parsers.cfg
$ANTLR4 CfgParser.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.common.parsers.cfg

# Titan Designer
# ASN1
cd $WORKSPACE_PATH/org.eclipse.titan.designer/src/org/eclipse/titan/designer/parsers/asn1parser/
$ANTLR4 Asn1Lexer.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.asn1parser
$ANTLR4 Asn1Parser.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.asn1parser

# TTCN3
cd $WORKSPACE_PATH/org.eclipse.titan.designer/src/org/eclipse/titan/designer/parsers/ttcn3parser/
$ANTLR4 PreprocessorDirectiveLexer.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.ttcn3parser
$ANTLR4 PreprocessorDirectiveParser.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.ttcn3parser
$ANTLR4 Ttcn3Lexer.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.ttcn3parser
$ANTLR4 Ttcn3KeywordlessLexer.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.ttcn3parser
$ANTLR4 Ttcn3CharstringLexer.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.ttcn3parser
$ANTLR4 Ttcn3Parser.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.ttcn3parser
$ANTLR4 Ttcn3Reparser.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.ttcn3parser
$ANTLR4 PatternStringLexer.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.ttcn3parser

# Extension attribute
cd $WORKSPACE_PATH/org.eclipse.titan.designer/src/org/eclipse/titan/designer/parsers/extensionattributeparser/
$ANTLR4 ExtensionAttributeLexer.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.extensionattributeparser
$ANTLR4 ExtensionAttributeParser.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.extensionattributeparser

# Variant attribute
cd $WORKSPACE_PATH/org.eclipse.titan.designer/src/org/eclipse/titan/designer/parsers/variantattributeparser/
$ANTLR4 VariantAttributeLexer.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.variantattributeparser
$ANTLR4 VariantAttributeParser.g4 -no-listener -no-visitor -encoding UTF-8 -package org.eclipse.titan.designer.parsers.variantattributeparser

# Generating ...LexerLogUtil.java files from ...Lexer.java files for resolving token names (OPTIONAL)
cd $WORKSPACE_PATH
$DIR/antlr4_generate_lexerlogutil.pl


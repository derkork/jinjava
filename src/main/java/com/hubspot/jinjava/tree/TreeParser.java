/**********************************************************************
 * Copyright (c) 2014 HubSpot Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **********************************************************************/
package com.hubspot.jinjava.tree;

import static com.hubspot.jinjava.tree.parse.TokenScannerSymbols.TOKEN_EXPR_START;
import static com.hubspot.jinjava.tree.parse.TokenScannerSymbols.TOKEN_FIXED;
import static com.hubspot.jinjava.tree.parse.TokenScannerSymbols.TOKEN_NOTE;
import static com.hubspot.jinjava.tree.parse.TokenScannerSymbols.TOKEN_TAG;

import java.util.LinkedList;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.interpret.MissingEndTagException;
import com.hubspot.jinjava.interpret.TemplateError;
import com.hubspot.jinjava.interpret.TemplateSyntaxException;
import com.hubspot.jinjava.interpret.UnexpectedTokenException;
import com.hubspot.jinjava.interpret.UnknownTagException;
import com.hubspot.jinjava.lib.tag.EndTag;
import com.hubspot.jinjava.lib.tag.Tag;
import com.hubspot.jinjava.tree.parse.ExpressionToken;
import com.hubspot.jinjava.tree.parse.TagToken;
import com.hubspot.jinjava.tree.parse.TextToken;
import com.hubspot.jinjava.tree.parse.Token;
import com.hubspot.jinjava.tree.parse.TokenScanner;

public class TreeParser {

  private final PeekingIterator<Token> scanner;
  private final JinjavaInterpreter interpreter;

  private Node parent;

  public TreeParser(JinjavaInterpreter interpreter, String input) {
    this.scanner = Iterators.peekingIterator(new TokenScanner(input, interpreter.getConfig()));
    this.interpreter = interpreter;
  }

  public Node buildTree() {
    Node root = new RootNode();

    parent = root;

    while (scanner.hasNext()) {

      Node node = nextNode();

      if (node != null) {
        if (node instanceof TextNode
            && parent instanceof TagNode
            && parent.getChildren().isEmpty()
            && parent.getMaster().isRightTrim()) {
          node.getMaster().setLeftTrim(true);
        }
        parent.getChildren().add(node);
      }
    }

    if (parent != root) {
      interpreter.addError(TemplateError.fromException(
          new MissingEndTagException(((TagNode) parent).getEndName(),
                                     parent.getMaster().getImage(),
                                     parent.getLineNumber())));
    }

    return root;
  }

  /**
   * @return null if EOF or error
   */
  private Node nextNode() {
    Token token = scanner.next();

    switch (token.getType()) {
      case TOKEN_FIXED:
        return text((TextToken) token);

      case TOKEN_EXPR_START:
        return expression((ExpressionToken) token);

      case TOKEN_TAG:
        return tag((TagToken) token);

      case TOKEN_NOTE:
        break;

      default:
        interpreter.addError(TemplateError.fromException(new UnexpectedTokenException(token.getImage(),
                                                                                      token.getLineNumber())));
    }

    return null;
  }

  private Node text(TextToken textToken) {
    if (interpreter.getConfig().isLstripBlocks()) {
      if (scanner.hasNext() && scanner.peek().getType() == TOKEN_TAG) {
        textToken = new TextToken(StringUtils.stripEnd(textToken.getImage(), "\t "), textToken.getLineNumber());
      }
    }

    TextNode n = new TextNode(textToken);
    n.setParent(parent);
    return n;
  }

  private Node expression(ExpressionToken expressionToken) {
    ExpressionNode n = new ExpressionNode(expressionToken);
    n.setParent(parent);
    return n;
  }

  private Node tag(TagToken tagToken) {
    Tag tag = interpreter.getContext().getTag(tagToken.getTagName());
    if (tag == null) {
      interpreter.addError(TemplateError.fromException(new UnknownTagException(tagToken)));
      return null;
    }

    if (tag instanceof EndTag) {
      endTag(tag, tagToken);
      return null;
    }

    TagNode node = new TagNode(tag, tagToken);
    node.setParent(parent);

    if (node.getEndName() != null) {
      parent.getChildren().add(node);
      parent = node;
      return null;
    }

    return node;
  }

  private void endTag(Tag tag, TagToken tagToken) {

    final LinkedList<Node> children = parent.getChildren();
    final Node lastChild = children.isEmpty() ? null : children.get(children.size() - 1);

    if (parent instanceof TagNode
        && tagToken.isLeftTrim()
        && lastChild != null
        && lastChild instanceof TextNode) {
      lastChild.getMaster().setRightTrim(true);
    }

    while (!(parent instanceof RootNode)) {
      TagNode parentTag = (TagNode) parent;
      parent = parent.getParent();

      if (parentTag.getEndName().equals(tag.getEndTagName())) {
        break;
      } else {
        interpreter.addError(TemplateError.fromException(
            new TemplateSyntaxException(tagToken.getImage(),
                                        "Mismatched end tag, expected: " + parentTag.getEndName(),
                                        tagToken.getLineNumber())));
      }
    }
  }

}

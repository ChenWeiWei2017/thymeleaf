/*
 * =============================================================================
 * 
 *   Copyright (c) 2011, The THYMELEAF team (http://www.thymeleaf.org)
 * 
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * 
 * =============================================================================
 */
package org.thymeleaf.dom;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.thymeleaf.Arguments;
import org.thymeleaf.Configuration;
import org.thymeleaf.processor.ProcessorAndContext;
import org.thymeleaf.processor.ProcessorResult;
import org.thymeleaf.util.CacheMap;
import org.thymeleaf.util.IdentityCounter;



/**
 * 
 * @author Daniel Fern&aacute;ndez
 * 
 * @since 1.2
 *
 */
public abstract class Node {

    
    private static CacheMap<String,String> NORMALIZED_NAMES = 
            new CacheMap<String, String>("Node.normalizedNames", true, 500);

    
    protected NestableNode parent;
    private boolean skippable;
    private boolean precomputed;
    private boolean recomputeProcessorsAfterEachExecution;
    private boolean recomputeProcessorsImmediately;
    
    private Map<String,Object> nodeLocalVariables;

    private List<ProcessorAndContext> processors;
    

    
    public static String normalizeName(final String name) {
        if (name == null) {
            return null;
        }
        final String normalizedName = NORMALIZED_NAMES.get(name);
        if (normalizedName != null) {
            return normalizedName;
        }
        final String newValue = name.toLowerCase();
        NORMALIZED_NAMES.put(name,  newValue);
        return newValue;
    }

    
    public static String applyDialectPrefix(final String name, final String dialectPrefix) {
        if (name == null) {
            return null;
        }
        if (dialectPrefix == null || dialectPrefix.trim().equals("")) {
            return name;
        }
        return dialectPrefix + ":" + name;
    }
    
    
    
    protected Node() {
        super();
        this.skippable = false;
        // Most types of node are not precomputable, so we set this to true as default to avoid
        // constant precomputations
        this.precomputed = true;
        this.recomputeProcessorsAfterEachExecution = false;
        this.recomputeProcessorsImmediately = false;
        this.nodeLocalVariables = null;
        this.processors = null;
    }
    
    
    public final boolean hasParent() {
        return this.parent != null;
    }
    
    public final NestableNode getParent() {
        return this.parent;
    }
    
    

    public final boolean getRecomputeProcessorsAfterEachExecution() {
        return this.recomputeProcessorsAfterEachExecution;
    }


    public final void setRecomputeProcessorsAfterEachExecution(final boolean recomputeProcessorsAfterEachExecution) {
        this.recomputeProcessorsAfterEachExecution = recomputeProcessorsAfterEachExecution;
    }


    
    public final boolean getRecomputeProcessorsImmediately() {
        return this.recomputeProcessorsImmediately;
    }


    public final void setRecomputeProcessorsImmediately(final boolean recomputeProcessorsImmediately) {
        this.recomputeProcessorsImmediately = recomputeProcessorsImmediately;
    }

    
    

    public final boolean isSkippable() {
        return this.skippable;
    }
    
    
    public final void setSkippable(final boolean skippable) {
        this.skippable = skippable;
        if (!skippable && this.parent != null) {
            // If this node is marked as non-skippable, set its parent as
            // non-skippable too.
            if (this.parent.isSkippable()) {
                this.parent.setSkippable(false);
            }
        }
        doAdditionalSkippableComputing(skippable);
    }
    
    abstract void doAdditionalSkippableComputing(final boolean isSkippable);

    
    
    
    final boolean isPrecomputed() {
        return this.precomputed;
    }
    
    final void setPrecomputed(final boolean precomputed) {
        this.precomputed = precomputed;
    }

    
    
    
    
    public final boolean hasNodeLocalVariables() {
        return this.nodeLocalVariables != null && this.nodeLocalVariables.size() > 0;
    }
    
    public final Map<String,Object> getNodeLocalVariables() {
        return this.nodeLocalVariables;
    }

    public final void addNodeLocalVariable(final String name, final Object value) {
        if (this.nodeLocalVariables == null) {
            this.nodeLocalVariables = new LinkedHashMap<String, Object>();
        }
        this.nodeLocalVariables.put(name,  value);
    }

    public final void addNodeLocalVariables(final Map<String,Object> variables) {
        if (variables != null) {
            for (final Map.Entry<String,Object> variablesEntry : variables.entrySet()) {
                addNodeLocalVariable(variablesEntry.getKey(), variablesEntry.getValue());
            }
        }
    }

    final void setNodeLocalVariables(final Map<String,Object> variables) {
        if (variables != null) {
            this.nodeLocalVariables = new LinkedHashMap<String,Object>(variables);
        } else { 
            this.nodeLocalVariables = null;
        }
    }



    
    final void precompute(final Configuration configuration) {

        if (!isPrecomputed()) {

            /*
             * Compute the processors that are applicable to this node
             */
            this.processors = configuration.computeProcessorsForNode(this);

            
            /*
             * Set skippability
             */
            if (this.processors == null || this.processors.size() == 0) {
                // We only set this specific node as skippable. If we executed
                // "setSkippable", the whole tree would be set as skippable, which
                // is unnecessary due to the fact that we are going to precompute
                // all of this node's children in a moment.
                // Also, note that if any of this node's children has processors
                // (and therefore sets itself as "non-skippable"), it will also
                // set its parent as non-skippable, overriding this action.
                this.skippable = true;
            } else {
                // This time we execute "setSkippable" so that all parents at all
                // levels are also set to "false"
                setSkippable(false);
            }

            
            /*
             * Set the "precomputed" flag to true 
             */
            setPrecomputed(true);

        }
        
        
        /*
         * Let subclasses add their own preprocessing
         */
        doAdditionalPrecompute(configuration);
     
    }
    
    
    abstract void doAdditionalPrecompute(final Configuration configuration);

    
    
    
    
    final void process(final Arguments arguments) {
        
        if (!isPrecomputed()) {
            precompute(arguments.getConfiguration());
        }
        
        if (this.recomputeProcessorsImmediately || this.recomputeProcessorsAfterEachExecution) {
            precompute(arguments.getConfiguration());
            this.recomputeProcessorsImmediately = false;
        }

        if (!isSkippable()) {
            
            /*
             *  If there are local variables at the node, add them to the ones at the
             *  Arguments object.
             */
            Arguments executionArguments =
                    (this.nodeLocalVariables != null && this.nodeLocalVariables.size() > 0?
                            arguments.addLocalVariables(this.nodeLocalVariables) : arguments);
            
            /* 
             * If the Arguments object has local variables, synchronize the node-local
             * variables map.
             */
            if (executionArguments.hasLocalVariables()) {
                setNodeLocalVariables(executionArguments.getLocalVariables());
            }
            
            /*
             * Perform the actual processing
             */
            if (hasParent() && this.processors != null && this.processors.size() > 0) {
                
                final IdentityCounter<ProcessorAndContext> alreadyExecuted = new IdentityCounter<ProcessorAndContext>(3);
                Arguments processingArguments = executionArguments;
                
                while (hasParent() && processingArguments != null) {
                    
                    // This way of executing processors allows processors to perform updates
                    // that might change which processors should be applied (for example, by
                    // adding or removing attributes)
                    processingArguments = 
                            applyNextProcessor(processingArguments, this, alreadyExecuted);
                    
                    if (processingArguments != null) {
                        // if we didn't reach the end of processor executions, update
                        // the Arguments object being used for processing
                        executionArguments = processingArguments;
                    }
                    
                    if (this.recomputeProcessorsImmediately || this.recomputeProcessorsAfterEachExecution) {
                        precompute(arguments.getConfiguration());
                        this.recomputeProcessorsImmediately = false;
                    }
                    
                }
                
            }
            
            doAdditionalProcess(executionArguments);
            
        }
    
    }
    
    
    
    
    
    private static final Arguments applyNextProcessor(final Arguments arguments, final Node node, final IdentityCounter<ProcessorAndContext> alreadyExecuted) {

        if (node.hasParent() && node.processors != null && node.processors.size() > 0) {

            for (final ProcessorAndContext processor : node.processors) {
                
                if (!alreadyExecuted.isAlreadyCounted(processor)) {
                    
                    Arguments executionArguments = arguments;

                    final ProcessorResult attrProcessorResult = 
                            processor.getProcessor().process(executionArguments, processor.getContext(), node);
                    
                    // The execution arguments need to be updated as instructed by the processor
                    // (for example, for adding local variables)
                    executionArguments = attrProcessorResult.computeNewArguments(executionArguments);
                    
                    // If we have added local variables, we should update the node's map for them in
                    // order to keep them synchronized
                    if (attrProcessorResult.hasLocalVariables()) {
                        node.setNodeLocalVariables(executionArguments.getLocalVariables());
                    }
                    
                    // Make sure this specific processor instance is not executed again
                    alreadyExecuted.count(processor);
                    
                    return executionArguments;
                    
                }
                
            }
            
        }

        // Either there are no processors, or all of them have already been processed
        return null;
        
    }
    
    
    
    abstract void doAdditionalProcess(final Arguments arguments);
    
    
    
    abstract void write(final Arguments arguments, final Writer writer) throws IOException;
    

    
    public final Node cloneNode(final NestableNode newParent, final boolean cloneProcessors) {
        final Node clone = createClonedInstance(newParent, cloneProcessors);
        cloneNodeInternals(clone, newParent, cloneProcessors);
        return clone;
    }
    
    

    abstract Node createClonedInstance(final NestableNode newParent, final boolean cloneProcessors);
    
    
    final void cloneNodeInternals(final Node node, final NestableNode newParent, final boolean cloneProcessors) {
        doCloneNodeInternals(node, newParent, cloneProcessors);
        if (cloneProcessors) {
            node.processors = this.processors;
            node.skippable = this.skippable;
            node.precomputed = this.precomputed;
        } else {
            node.processors = null;
            node.skippable = false;
            node.precomputed = false;
        }
        node.parent = newParent;
        if (this.nodeLocalVariables != null) {
            node.nodeLocalVariables = new LinkedHashMap<String, Object>(this.nodeLocalVariables);
        }
    }

    
    abstract void doCloneNodeInternals(final Node node, final NestableNode newParent, final boolean cloneProcessors);
    
    
    
    
    
    public static final Node translateDOMNode(final org.w3c.dom.Node domNode, final NestableNode parentNode) {
        
        if (domNode instanceof org.w3c.dom.Element) {
            return Tag.translateDOMTag((org.w3c.dom.Element)domNode, parentNode);
        } else if (domNode instanceof org.w3c.dom.Comment) {
            return Comment.translateDOMComment((org.w3c.dom.Comment)domNode, parentNode);
        } else if (domNode instanceof org.w3c.dom.CDATASection) {
            return CDATASection.translateDOMCDATASection((org.w3c.dom.CDATASection)domNode, parentNode);
        } else if (domNode instanceof org.w3c.dom.Text) {
            return Text.translateDOMText((org.w3c.dom.Text)domNode, parentNode);
        } else {
            throw new IllegalArgumentException(
                    "Node " + domNode.getNodeName() + " of type " + domNode.getNodeType() + 
                    " and class " + domNode.getClass().getName() + " cannot be translated to " +
                    "Thymeleaf's DOM representation.");
        }
        
    }
    
    
}


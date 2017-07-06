/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 *
 */

package ai.grakn.generator;

import ai.grakn.Grakn;
import ai.grakn.GraknGraph;
import ai.grakn.GraknSession;
import ai.grakn.GraknTxType;
import ai.grakn.concept.Concept;
import ai.grakn.concept.Entity;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.Label;
import ai.grakn.concept.OntologyConcept;
import ai.grakn.concept.Relation;
import ai.grakn.concept.RelationType;
import ai.grakn.concept.Resource;
import ai.grakn.concept.ResourceType;
import ai.grakn.concept.Role;
import ai.grakn.concept.Rule;
import ai.grakn.concept.RuleType;
import ai.grakn.concept.Thing;
import ai.grakn.concept.Type;
import ai.grakn.exception.GraphOperationException;
import ai.grakn.util.CommonUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.pholser.junit.quickcheck.MinimalCounterexampleHook;
import com.pholser.junit.quickcheck.generator.GeneratorConfiguration;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.grakn.util.StringUtil.valueToString;
import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.stream.Collectors.joining;

//import static ai.grakn.graql.Graql.var;

/**
 * Generator to create random {@link GraknGraph}s.
 */
@SuppressWarnings("unchecked") // We're performing random operations. Generics will not constrain us!
public class GraknGraphs extends AbstractGenerator<GraknGraph> implements MinimalCounterexampleHook {

    private static GraknGraph lastGeneratedGraph;

    private static StringBuilder graphSummary;

    private GraknGraph graph;
    private Boolean open = null;

    public GraknGraphs() {
        super(GraknGraph.class);
    }

    public static GraknGraph lastGeneratedGraph() {
        return lastGeneratedGraph;
    }

    /**
     * Mutate the graph being generated by calling a random method on it.
     */
    private void mutateOnce() {
        boolean succesfulMutation = false;

        while (!succesfulMutation) {
            succesfulMutation = true;
            try {
                random.choose(mutators).run();
            } catch (UnsupportedOperationException | GraphOperationException | GraphGeneratorException e) {
                // We only catch acceptable exceptions for the graph API to throw
                succesfulMutation = false;
            }
        }
    }

    @Override
    public GraknGraph generate() {
        // TODO: Generate more keyspaces
        // We don't do this now because creating lots of keyspaces seems to slow the system graph
        String keyspace = gen().make(MetasyntacticStrings.class).generate(random, status);
        GraknSession factory = Grakn.session(Grakn.IN_MEMORY, keyspace);

        int size = status.size();

        graphSummary = new StringBuilder();
        graphSummary.append("size: ").append(size).append("\n");

        closeGraph(lastGeneratedGraph);

        // Clear graph before retrieving
        graph = factory.open(GraknTxType.WRITE);
        graph.admin().delete();
        graph = factory.open(GraknTxType.WRITE);

        for (int i = 0; i < size; i++) {
            mutateOnce();
        }

        // Close graphs randomly, unless parameter is set
        boolean shouldOpen = open != null ? open : random.nextBoolean();

        if (!shouldOpen) graph.close();

        lastGeneratedGraph = graph;
        return graph;
    }

    private void closeGraph(GraknGraph graph){
        if(graph != null && !graph.isClosed()){
            graph.close();
        }
    }

    public void configure(Open open) {
        setOpen(open.value());
    }

    public GraknGraphs setOpen(boolean open) {
        this.open = open;
        return this;
    }

    // A list of methods that will mutate the graph in some random way when called
    private final ImmutableList<Runnable> mutators = ImmutableList.of(
            () -> {
                Label label = typeLabel();
                EntityType superType = entityType();
                EntityType entityType = graph.putEntityType(label).sup(superType);
                summaryAssign(entityType, "graph", "putEntityType", label);
                summary(entityType, "superType", superType);
            },
            () -> {
                Label label = typeLabel();
                ResourceType.DataType dataType = gen(ResourceType.DataType.class);
                ResourceType superType = resourceType();
                ResourceType resourceType = graph.putResourceType(label, dataType).sup(superType);
                summaryAssign(resourceType, "graph", "putResourceType", label, dataType);
                summary(resourceType, "superType", superType);
            },
            () -> {
                Label label = typeLabel();
                Role superType = roleType();
                Role role = graph.putRole(label).sup(superType);
                summaryAssign(role, "graph", "putRoleType", label);
                summary(role, "superType", superType);
            },
            () -> {
                Label label = typeLabel();
                RelationType superType = relationType();
                RelationType relationType = graph.putRelationType(label).sup(superType);
                summaryAssign(relationType, "graph", "putRelationType", label);
                summary(relationType, "superType", superType);
            },
            () -> {
                boolean flag = gen(Boolean.class);
                graph.showImplicitConcepts(flag);
                summary("graph", "showImplicitConcepts", flag);
            },
            () -> {
                Type type = type();
                Role role = roleType();
                type.plays(role);
                summary(type, "plays", role);
            },
            () -> {
                Type type = type();
                ResourceType resourceType = resourceType();
                type.resource(resourceType);
                summary(type, "resource", resourceType);
            },
            () -> {
                Type type = type();
                ResourceType resourceType = resourceType();
                type.key(resourceType);
                summary(type, "key", resourceType);
            },
            () -> {
                Type type = type();
                boolean isAbstract = random.nextBoolean();
                type.setAbstract(isAbstract);
                summary(type, "setAbstract", isAbstract);
            },
            () -> {
                EntityType entityType1 = entityType();
                EntityType entityType2 = entityType();
                entityType1.sup(entityType2);
                summary(entityType1, "superType", entityType2);
            },
            () -> {
                EntityType entityType = entityType();
                Entity entity = entityType.addEntity();
                summaryAssign(entity, entityType, "addEntity");
            },
            () -> {
                Role role1 = roleType();
                Role role2 = roleType();
                role1.sup(role2);
                summary(role1, "superType", role2);
            },
            () -> {
                RelationType relationType1 = relationType();
                RelationType relationType2 = relationType();
                relationType1.sup(relationType2);
                summary(relationType1, "superType", relationType2);
            },
            () -> {
                RelationType relationType = relationType();
                Relation relation = relationType.addRelation();
                summaryAssign(relation, relationType, "addRelation");
            },
            () -> {
                RelationType relationType = relationType();
                Role role = roleType();
                relationType.relates(role);
                summary(relationType, "relates", role);
            },
            () -> {
                ResourceType resourceType1 = resourceType();
                ResourceType resourceType2 = resourceType();
                resourceType1.sup(resourceType2);
                summary(resourceType1, "superType", resourceType2);
            },
            () -> {
                ResourceType resourceType = resourceType();
                Object value = gen().make(ResourceValues.class).generate(random, status);
                Resource resource = resourceType.putResource(value);
                summaryAssign(resource, resourceType, "putResource", valueToString(value));
            },
            // () -> resourceType().setRegex(gen(String.class)), // TODO: Enable this when doesn't throw a NPE
            () -> {
                RuleType ruleType1 = ruleType();
                RuleType ruleType2 = ruleType();
                ruleType1.sup(ruleType2);
                summary(ruleType1, "superType", ruleType2);
            },
            //TODO: re-enable when grakn-graph can create graql constructs
            /*() -> {
                RuleType ruleType = ruleType();
                Rule rule = ruleType.putRule(graql.parsePattern("$x"), graql.parsePattern("$x"));// TODO: generate more complicated rules
                summaryAssign(rule, ruleType, "putRule", "var(\"x\")", "var(\"y\")");
            },*/
            () -> {
                Thing thing = instance();
                Resource resource = resource();
                thing.resource(resource);
                summary(thing, "resource", resource);
            },
            () -> {
                OntologyConcept type = ontologyConcept();
                Thing thing = instance();
                //TODO: Clean this up
                if(type instanceof Type) {
                    ((Type)type).scope(thing);
                }
                summary(type, "scope", thing);
            },
            () -> {
                Relation relation = relation();
                Role role = roleType();
                Thing thing = instance();
                relation.addRolePlayer(role, thing);
                summary(relation, "addRolePlayer", role, thing);
            }
    );

    private void summary(Object target, String methodName, Object... args) {
        graphSummary.append(summaryFormat(target)).append(".").append(methodName).append("(");
        graphSummary.append(Stream.of(args).map(this::summaryFormat).collect(joining(", ")));
        graphSummary.append(");\n");
    }

    private void summaryAssign(Object assign, Object target, String methodName, Object... args) {
        summary(summaryFormat(assign) + " = " + summaryFormat(target), methodName, args);
    }

    private String summaryFormat(Object object) {
        if (object instanceof Type) {
            return ((Type) object).getLabel().getValue().replaceAll("-", "_");
        } else if (object instanceof Thing) {
            Thing thing = (Thing) object;
            return summaryFormat(thing.type()) + thing.getId().getValue();
        } else if (object instanceof Label) {
            return valueToString(((Label) object).getValue());
        } else {
            return object.toString();
        }
    }

    private Label typeLabel() {
        return gen().make(TypeLabels.class, gen().make(MetasyntacticStrings.class)).generate(random, status);
    }

    private OntologyConcept ontologyConcept() {
        return random.choose(graph.admin().getMetaConcept().subs());
    }

    private Type type() {
        Collection<? extends Type> candidates = graph.admin().getMetaConcept().subs().stream().
                filter(o -> !o.isRole()).map(o -> (Type) o).collect(Collectors.toSet());
        return random.choose(candidates);
    }

    private EntityType entityType() {
        return random.choose(graph.admin().getMetaEntityType().subs());
    }

    private Role roleType() {
        return random.choose(graph.admin().getMetaRoleType().subs());
    }

    private ResourceType resourceType() {
        return random.choose((Collection<ResourceType>) graph.admin().getMetaResourceType().subs());
    }

    private RelationType relationType() {
        return random.choose(graph.admin().getMetaRelationType().subs());
    }

    private RuleType ruleType() {
        return random.choose(graph.admin().getMetaRuleType().subs());
    }

    private Thing instance() {
        Set<? extends Thing> candidates = graph.admin().getMetaConcept().subs().stream().
                filter(element -> !element.isRole()).
                flatMap(element -> ((Type) element).instances().stream()).
                collect(Collectors.toSet());
        return chooseOrThrow(candidates);
    }

    private Relation relation() {
        return chooseOrThrow(graph.admin().getMetaRelationType().instances());
    }

    private Resource resource() {
        return chooseOrThrow((Collection<Resource>) graph.admin().getMetaResourceType().instances());
    }

    private Rule rule() {
        return chooseOrThrow(graph.admin().getMetaRuleType().instances());
    }

    private <T> T chooseOrThrow(Collection<? extends T> collection) {
        if (collection.isEmpty()) {
            throw new GraphGeneratorException();
        } else {
            return random.choose(collection);
        }
    }

    public static List<Concept> allConceptsFrom(GraknGraph graph) {
        List<Concept> concepts = Lists.newArrayList(GraknGraphs.allOntologyElementsFrom(graph));
        concepts.addAll(allInstancesFrom(graph));
        return concepts;
    }

    public static Collection<? extends OntologyConcept> allOntologyElementsFrom(GraknGraph graph) {
        Function<GraknGraph, ? extends Collection<? extends OntologyConcept>> function = g -> g.admin().getMetaConcept().subs();
        return CommonUtil.withImplicitConceptsVisible(graph, function);
    }

    public static Collection<? extends Thing> allInstancesFrom(GraknGraph graph) {
        Function<GraknGraph, ? extends Collection<? extends Thing>> function = g -> g.admin().getMetaConcept().subs().stream().
                filter(element -> !element.isRole()).
                flatMap(element -> ((Type) element).instances().stream()).
                collect(Collectors.toSet());
        return CommonUtil.withImplicitConceptsVisible(graph, function);
    }

    @Override
    public void handle(Object[] counterexample, Runnable action) {
        System.err.println("Graph generated:\n" + graphSummary);
    }

    @Target({PARAMETER, FIELD, ANNOTATION_TYPE, TYPE_USE})
    @Retention(RUNTIME)
    @GeneratorConfiguration
    public @interface Open {
        boolean value() default true;
    }

    private class GraphGeneratorException extends RuntimeException {

    }
}

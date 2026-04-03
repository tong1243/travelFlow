package com.example.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isNative;
import static net.bytebuddy.matcher.ElementMatchers.isTypeInitializer;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class TimingAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        AgentConfig config = AgentConfig.parse(agentArgs);
        AgentRuntime.init(config);
        System.out.println("[Agent] premain loaded, " + config);

        ElementMatcher<TypeDescription> matcher = typeDescription ->
                AgentRuntime.shouldInstrument(typeDescription.getName());

        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .ignore(typeDescription -> !AgentRuntime.shouldInstrument(typeDescription.getName()))
                .type(matcher)
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(
                                Advice.to(TimingAdvice.class)
                                        .on(
                                                isMethod()
                                                        .and(not(isConstructor()))
                                                        .and(not(isTypeInitializer()))
                                                        .and(not(isAbstract()))
                                                        .and(not(isNative()))
                                        )
                        )
                )
                .installOn(inst);
    }
}

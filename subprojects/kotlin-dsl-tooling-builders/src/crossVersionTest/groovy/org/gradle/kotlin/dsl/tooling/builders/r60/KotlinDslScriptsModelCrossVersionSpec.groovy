/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.tooling.builders.r60

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.kotlin.dsl.tooling.builders.KotlinDslScriptsModelClient
import org.gradle.kotlin.dsl.tooling.builders.KotlinDslScriptsModelRequest
import org.gradle.kotlin.dsl.tooling.models.KotlinDslScriptsModel

import java.lang.reflect.Proxy

import static org.gradle.integtests.tooling.fixture.TextUtil.escapeString


@TargetGradleVersion(">=6.0")
class KotlinDslScriptsModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    def "can fetch model for the scripts of a build"() {

        given:
        withBuildSrc()
        def rootJar = withEmptyJar("classes_root.jar").canonicalFile
        def aJar = withEmptyJar("classes_a.jar").canonicalFile
        def bJar = withEmptyJar("classes_b.jar").canonicalFile

        and:
        def settings = withSettings("""
            include("a", "b")
        """)
        def root = withBuildScript("""
            buildscript {
                dependencies {
                    classpath(files("${escapeString(rootJar)}"))
                }
            }
        """)
        def a = withBuildScriptIn("a", """
            buildscript {
                dependencies {
                    classpath(files("${escapeString(aJar)}"))
                }
            }
        """)
        def b = withBuildScriptIn("b", """
            plugins {
                `kotlin-dsl`
            }
            buildscript {
                dependencies {
                    classpath(files("${escapeString(bJar)}"))
                }
            }
            $repositoriesBlock
        """)
        def foo = withFile("b/src/main/kotlin/foo/foo.gradle.kts", "")

        when:
        def model = kotlinDslScriptsModelFor()

        then:
        model.scriptModels.size() == 5
        model.scriptModels.keySet() == [settings, root, a, b, foo] as Set

        and:
        def settingsClassPath = model.scriptModels[settings].classPath.collect { it.name }
        !settingsClassPath.contains("buildSrc.jar")
        !settingsClassPath.contains(rootJar.name)
        !settingsClassPath.contains(aJar.name)
        !settingsClassPath.contains(bJar.name)

        and:
        def rootClassPath = model.scriptModels[root].classPath.collect { it.name }
        rootClassPath.contains("buildSrc.jar")
        rootClassPath.contains(rootJar.name)
        !rootClassPath.contains(aJar.name)
        !rootClassPath.contains(bJar.name)

        and:
        def aClassPath = model.scriptModels[a].classPath.collect { it.name }
        aClassPath.contains("buildSrc.jar")
        aClassPath.contains(rootJar.name)
        aClassPath.contains(aJar.name)
        !aClassPath.contains(bJar.name)

        and:
        def bClassPath = model.scriptModels[b].classPath.collect { it.name }
        bClassPath.contains("buildSrc.jar")
        bClassPath.contains(rootJar.name)
        !bClassPath.contains(aJar.name)
        bClassPath.contains(bJar.name)

        and:
        def fooClassPath = model.scriptModels[foo].classPath.collect { it.name }
        !fooClassPath.contains("buildSrc.jar")
        !fooClassPath.contains(rootJar.name)
        !fooClassPath.contains(aJar.name)
        !fooClassPath.contains(bJar.name)
    }

    def "can fetch model for a set of scripts"() {

        given:
        withBuildSrc()
        def rootJar = withEmptyJar("classes_root.jar").canonicalFile
        def aJar = withEmptyJar("classes_a.jar").canonicalFile
        def bJar = withEmptyJar("classes_b.jar").canonicalFile

        and:
        def settings = withSettings("""
            include("a", "b")
        """)
        def root = withBuildScript("""
            buildscript {
                dependencies {
                    classpath(files("${escapeString(rootJar)}"))
                }
            }
        """)
        def a = withBuildScriptIn("a", """
            buildscript {
                dependencies {
                    classpath(files("${escapeString(aJar)}"))
                }
            }
        """)
        def b = withBuildScriptIn("b", """
            buildscript {
                dependencies {
                    classpath(files("${escapeString(bJar)}"))
                }
            }
        """)
        def scriptFiles = [settings, root, a, b]

        when:
        def model = kotlinDslScriptsModelFor(*scriptFiles)

        then:
        model.scriptModels.size() == 4

        and:
        def settingsClassPath = model.scriptModels[settings].classPath.collect { it.name }
        !settingsClassPath.contains("buildSrc.jar")
        !settingsClassPath.contains(rootJar.name)
        !settingsClassPath.contains(aJar.name)
        !settingsClassPath.contains(bJar.name)

        and:
        def rootClassPath = model.scriptModels[root].classPath.collect { it.name }
        rootClassPath.contains("buildSrc.jar")
        rootClassPath.contains(rootJar.name)
        !rootClassPath.contains(aJar.name)
        !rootClassPath.contains(bJar.name)

        and:
        def aClassPath = model.scriptModels[a].classPath.collect { it.name }
        aClassPath.contains("buildSrc.jar")
        aClassPath.contains(rootJar.name)
        aClassPath.contains(aJar.name)
        !aClassPath.contains(bJar.name)

        and:
        def bClassPath = model.scriptModels[b].classPath.collect { it.name }
        bClassPath.contains("buildSrc.jar")
        bClassPath.contains(rootJar.name)
        !bClassPath.contains(aJar.name)
        bClassPath.contains(bJar.name)
    }

    def "multi-scripts model is dehydrated over the wire"() {

        given:
        withBuildSrc()
        buildFileKts << ""

        when:
        def model = kotlinDslScriptsModelFor(buildFileKts)

        then:
        def source = Proxy.getInvocationHandler(model).sourceObject
        source.scripts == [buildFileKts]

        and:
        def commonModel = source.commonModel
        commonModel != null
        commonModel.classPath.size() > 0
        // commonModel.classPath.find { it.name == "buildSrc.jar" } != null // TODO
        commonModel.sourcePath.size() > 0
        commonModel.implicitImports.size() > 0

        and:
        def scriptModels = source.dehydratedScriptModels
        scriptModels != null
        scriptModels.size() == 1

        and:
        def buildFileKtsModel = source.dehydratedScriptModels.get(buildFileKts)
        buildFileKtsModel != null
        buildFileKtsModel.classPath.isEmpty()
        buildFileKtsModel.sourcePath.isEmpty()
        buildFileKtsModel.implicitImports.isEmpty()
        buildFileKtsModel.editorReports.isEmpty()
        buildFileKtsModel.exceptions.isEmpty()
    }

    private KotlinDslScriptsModel kotlinDslScriptsModelFor(File... scripts) {
        return withConnection { connection ->
            new KotlinDslScriptsModelClient().fetchKotlinDslScriptsModel(
                connection,
                new KotlinDslScriptsModelRequest(scripts.toList())
            )
        }
    }
}

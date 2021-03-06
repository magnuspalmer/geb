/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package geb.gradle.saucelabs

import geb.gradle.saucelabs.task.StartSauceConnect
import geb.gradle.saucelabs.task.StopSauceConnect
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.testing.Test

class SaucePlugin implements Plugin<Project> {

	Project project

	@Override
	void apply(Project project) {
		this.project = project

		project.configurations.create('sauceConnect')

		project.extensions.create('sauceLabs', SauceLabsExtension, project).addExtensions()

		addTunnelTasks()
		addSauceTasks()
	}

	void addSauceTasks() {
		def allSauceTests = project.task("allSauceTests") {
			group "Sauce Test"
		}

		project.sauceLabs.browsers.all { BrowserSpec browser ->
			def testTask = project.task("${browser.displayName}Test", type: Test) { Test task ->
				group allSauceTests.group
				task.dependsOn 'openSauceTunnelInBackground'
				allSauceTests.dependsOn task
				finalizedBy 'closeSauceTunnel'

				systemProperty 'geb.build.reportsDir', project.reporting.file("$name-geb")

				browser.configure(task)
			}

			def decorateReportsTask = project.task("${browser.displayName}DecorateReports", type: Copy) {
				from testTask.reports.junitXml.destination
				into "${testTask.reports.junitXml.destination}-decorated"
				filter { it.replaceAll("(testsuite|testcase) name=\"(.+?)\"", "\$1 name=\"\$2 ($browser.displayName)\"") }
			}

			testTask.finalizedBy decorateReportsTask
		}
	}

	void addTunnelTasks() {
		project.task('closeSauceTunnel', type: StopSauceConnect) {
			sauceConnect = project.sauceLabs.connect
		}

		def openSauceTunnel = project.task('openSauceTunnel', type: StartSauceConnect)

		def openSauceTunnelInBackground = project.task('openSauceTunnelInBackground', type: StartSauceConnect) {
			inBackground = true
			finalizedBy 'closeSauceTunnel'
		}

		[openSauceTunnel, openSauceTunnelInBackground].each {
			it.configure {
				sauceConnect = project.sauceLabs.connect
				workingDir = project.buildDir
				conventionMapping.sauceConnectJar = { project.configurations.sauceConnect.singleFile }
			}
		}
	}
}

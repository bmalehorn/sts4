/*******************************************************************************
 * Copyright (c) 2016, 2022 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.handlers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.common.IJavaProjectReconcileEngine;
import org.springframework.ide.vscode.boot.java.reconcilers.JavaReconciler;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.LazyTextDocument;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class BootJavaReconcileEngine implements IReconcileEngine, IJavaProjectReconcileEngine {
		
	private static final Logger log = LoggerFactory.getLogger(BootJavaReconcileEngine.class);

	private final SimpleTextDocumentService documents;
	private final JavaProjectFinder projectFinder; 
	private JavaReconciler[] javaReconcilers;
	
	public BootJavaReconcileEngine(JavaProjectFinder projectFinder, JavaReconciler[] javaReconcilers, SimpleTextDocumentService documents) {
		this.documents = documents;
		this.projectFinder = projectFinder;
		this.javaReconcilers = javaReconcilers;
	}

	@Override
	public void reconcile(final IDocument doc, final IProblemCollector problemCollector) {
		IJavaProject project = projectFinder.find(new TextDocumentIdentifier(doc.getUri())).orElse(null);
		if (project != null) {
			
			try {
				problemCollector.beginCollecting();
				
				AtomicInteger count = new AtomicInteger(0);
				
				IProblemCollector recolerProblemCollector = new IProblemCollector() {
		
					@Override
					public void beginCollecting() {
					}
		
					@Override
					public synchronized void endCollecting() {
						// If count is reached then let the endCollecting be called when all completable futures resolved
						if (count.incrementAndGet() < javaReconcilers.length) {
							problemCollector.checkPointCollecting();
						}
					}
		
					@Override
					public synchronized void accept(ReconcileProblem problem) {
						problemCollector.accept(problem);
					}
					
				};
				
				for (JavaReconciler jr : javaReconcilers) {
					try {
						jr.reconcile(project, doc, recolerProblemCollector);
					} catch (Exception e) {
						log.error("", e);
					}
				}
				
				problemCollector.endCollecting();
				
//				CompletableFuture<?>[] futures = Arrays.stream(javaReconcilers)
//						.map(jr -> CompletableFuture.runAsync(() -> jr.reconcile(project, doc, recolerProblemCollector)).exceptionally(t -> null))
//						.toArray(CompletableFuture[]::new);
//				
//				CompletableFuture.allOf(futures).thenAccept(a -> problemCollector.endCollecting()).get();
				
			} catch (Exception e) {
				log.error("", e);
			} finally {
				problemCollector.endCollecting();
			}
		
		}
	}

	@Override
	public void reconcile(IJavaProject project, Function<TextDocument, IProblemCollector> problemCollectorFactory) {
		Stream<Path> files = IClasspathUtil.getProjectJavaSourceFolders(project.getClasspath()).flatMap(folder -> {
			try {
				return Files.walk(folder.toPath());
			} catch (IOException e) {
				return Stream.empty();
			}
		});
		Stream<TextDocumentIdentifier> docIds = files.filter(f -> {
			return Files.isRegularFile(f) && f.getFileName().toString().endsWith(".java");
		}).map(f -> new TextDocumentIdentifier(f.toUri().toString()));

		List<TextDocument> docs = docIds.filter(docId -> documents.getLatestSnapshot(docId.getUri()) == null)
				.map(docId -> new LazyTextDocument(docId.getUri(), LanguageId.JAVA)).collect(Collectors.toList());

		Map<IDocument, IProblemCollector> problemCollectors = docs.stream()
				.collect(Collectors.toMap(d -> d, d -> problemCollectorFactory.apply(d)));

		problemCollectors.values().forEach(c -> c.beginCollecting());

		for (JavaReconciler jr : javaReconcilers) {
			try {
				Map<IDocument, Collection<ReconcileProblem>> problems = jr.reconcile(project, docs,
						problemCollectorFactory);
				problems.entrySet().forEach(e -> {
					IProblemCollector collector = problemCollectors.get(e.getKey());
					e.getValue().forEach(p -> collector.accept(p));
				});
			} catch (Exception e) {
				log.error("", e);
			}
		}

		problemCollectors.values().forEach(c -> c.endCollecting());

	}

}

/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2010-2012, Stefan Lay <stefan.lay@sap.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.jboss.forge.jgit.api;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jboss.forge.jgit.api.Git;
import org.jboss.forge.jgit.api.GitCommand;
import org.jboss.forge.jgit.api.MergeCommand;
import org.jboss.forge.jgit.api.MergeResult;
import org.jboss.forge.jgit.api.MergeResult.MergeStatus;
import org.jboss.forge.jgit.api.errors.CheckoutConflictException;
import org.jboss.forge.jgit.api.errors.ConcurrentRefUpdateException;
import org.jboss.forge.jgit.api.errors.GitAPIException;
import org.jboss.forge.jgit.api.errors.InvalidMergeHeadsException;
import org.jboss.forge.jgit.api.errors.JGitInternalException;
import org.jboss.forge.jgit.api.errors.NoHeadException;
import org.jboss.forge.jgit.api.errors.NoMessageException;
import org.jboss.forge.jgit.api.errors.WrongRepositoryStateException;
import org.jboss.forge.jgit.dircache.DirCacheCheckout;
import org.jboss.forge.jgit.internal.JGitText;
import org.jboss.forge.jgit.lib.AnyObjectId;
import org.jboss.forge.jgit.lib.Constants;
import org.jboss.forge.jgit.lib.ObjectId;
import org.jboss.forge.jgit.lib.ObjectIdRef;
import org.jboss.forge.jgit.lib.Ref;
import org.jboss.forge.jgit.lib.RefUpdate;
import org.jboss.forge.jgit.lib.Repository;
import org.jboss.forge.jgit.lib.Ref.Storage;
import org.jboss.forge.jgit.lib.RefUpdate.Result;
import org.jboss.forge.jgit.merge.MergeMessageFormatter;
import org.jboss.forge.jgit.merge.MergeStrategy;
import org.jboss.forge.jgit.merge.Merger;
import org.jboss.forge.jgit.merge.ResolveMerger;
import org.jboss.forge.jgit.merge.SquashMessageFormatter;
import org.jboss.forge.jgit.merge.ResolveMerger.MergeFailureReason;
import org.jboss.forge.jgit.revwalk.RevCommit;
import org.jboss.forge.jgit.revwalk.RevWalk;
import org.jboss.forge.jgit.revwalk.RevWalkUtils;
import org.jboss.forge.jgit.treewalk.FileTreeIterator;

/**
 * A class used to execute a {@code Merge} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-merge.html"
 *      >Git documentation about Merge</a>
 */
public class MergeCommand extends GitCommand<MergeResult> {

	private MergeStrategy mergeStrategy = MergeStrategy.RESOLVE;

	private List<Ref> commits = new LinkedList<Ref>();

	private boolean squash;

	/**
	 * @param repo
	 */
	protected MergeCommand(Repository repo) {
		super(repo);
	}

	/**
	 * Executes the {@code Merge} command with all the options and parameters
	 * collected by the setter methods (e.g. {@link #include(Ref)}) of this
	 * class. Each instance of this class should only be used for one invocation
	 * of the command. Don't call this method twice on an instance.
	 *
	 * @return the result of the merge
	 */
	public MergeResult call() throws GitAPIException, NoHeadException,
			ConcurrentRefUpdateException, CheckoutConflictException,
			InvalidMergeHeadsException, WrongRepositoryStateException, NoMessageException {
		checkCallable();

		if (commits.size() != 1)
			throw new InvalidMergeHeadsException(
					commits.isEmpty() ? JGitText.get().noMergeHeadSpecified
							: MessageFormat.format(
									JGitText.get().mergeStrategyDoesNotSupportHeads,
									mergeStrategy.getName(),
									Integer.valueOf(commits.size())));

		RevWalk revWalk = null;
		DirCacheCheckout dco = null;
		try {
			Ref head = repo.getRef(Constants.HEAD);
			if (head == null)
				throw new NoHeadException(
						JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported);
			StringBuilder refLogMessage = new StringBuilder("merge ");

			// Check for FAST_FORWARD, ALREADY_UP_TO_DATE
			revWalk = new RevWalk(repo);

			// we know for now there is only one commit
			Ref ref = commits.get(0);

			refLogMessage.append(ref.getName());

			// handle annotated tags
			ObjectId objectId = ref.getPeeledObjectId();
			if (objectId == null)
				objectId = ref.getObjectId();

			RevCommit srcCommit = revWalk.lookupCommit(objectId);

			ObjectId headId = head.getObjectId();
			if (headId == null) {
				revWalk.parseHeaders(srcCommit);
				dco = new DirCacheCheckout(repo,
						repo.lockDirCache(), srcCommit.getTree());
				dco.setFailOnConflict(true);
				dco.checkout();
				RefUpdate refUpdate = repo
						.updateRef(head.getTarget().getName());
				refUpdate.setNewObjectId(objectId);
				refUpdate.setExpectedOldObjectId(null);
				refUpdate.setRefLogMessage("initial pull", false);
				if (refUpdate.update() != Result.NEW)
					throw new NoHeadException(
							JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported);
				setCallable(false);
				return new MergeResult(srcCommit, srcCommit, new ObjectId[] {
						null, srcCommit }, MergeStatus.FAST_FORWARD,
						mergeStrategy, null, null);
			}

			RevCommit headCommit = revWalk.lookupCommit(headId);

			if (revWalk.isMergedInto(srcCommit, headCommit)) {
				setCallable(false);
				return new MergeResult(headCommit, srcCommit, new ObjectId[] {
						headCommit, srcCommit },
						MergeStatus.ALREADY_UP_TO_DATE, mergeStrategy, null, null);
			} else if (revWalk.isMergedInto(headCommit, srcCommit)) {
				// FAST_FORWARD detected: skip doing a real merge but only
				// update HEAD
				refLogMessage.append(": " + MergeStatus.FAST_FORWARD);
				dco = new DirCacheCheckout(repo,
						headCommit.getTree(), repo.lockDirCache(),
						srcCommit.getTree());
				dco.setFailOnConflict(true);
				dco.checkout();
				String msg = null;
				ObjectId newHead, base = null;
				MergeStatus mergeStatus = null;
				if (!squash) {
					updateHead(refLogMessage, srcCommit, headId);
					newHead = base = srcCommit;
					mergeStatus = MergeStatus.FAST_FORWARD;
				} else {
					msg = JGitText.get().squashCommitNotUpdatingHEAD;
					newHead = base = headId;
					mergeStatus = MergeStatus.FAST_FORWARD_SQUASHED;
					List<RevCommit> squashedCommits = RevWalkUtils.find(
							revWalk, srcCommit, headCommit);
					String squashMessage = new SquashMessageFormatter().format(
							squashedCommits, head);
					repo.writeSquashCommitMsg(squashMessage);
				}
				setCallable(false);
				return new MergeResult(newHead, base, new ObjectId[] {
						headCommit, srcCommit }, mergeStatus, mergeStrategy,
						null, msg);
			} else {
				String mergeMessage = "";
				if (!squash) {
					mergeMessage = new MergeMessageFormatter().format(
							commits, head);
					repo.writeMergeCommitMsg(mergeMessage);
					repo.writeMergeHeads(Arrays.asList(ref.getObjectId()));
				} else {
					List<RevCommit> squashedCommits = RevWalkUtils.find(
							revWalk, srcCommit, headCommit);
					String squashMessage = new SquashMessageFormatter().format(
							squashedCommits, head);
					repo.writeSquashCommitMsg(squashMessage);
				}
				Merger merger = mergeStrategy.newMerger(repo);
				boolean noProblems;
				Map<String, org.jboss.forge.jgit.merge.MergeResult<?>> lowLevelResults = null;
				Map<String, MergeFailureReason> failingPaths = null;
				List<String> unmergedPaths = null;
				if (merger instanceof ResolveMerger) {
					ResolveMerger resolveMerger = (ResolveMerger) merger;
					resolveMerger.setCommitNames(new String[] {
							"BASE", "HEAD", ref.getName() });
					resolveMerger.setWorkingTreeIterator(new FileTreeIterator(repo));
					noProblems = merger.merge(headCommit, srcCommit);
					lowLevelResults = resolveMerger
							.getMergeResults();
					failingPaths = resolveMerger.getFailingPaths();
					unmergedPaths = resolveMerger.getUnmergedPaths();
				} else
					noProblems = merger.merge(headCommit, srcCommit);
				refLogMessage.append(": Merge made by ");
				refLogMessage.append(mergeStrategy.getName());
				refLogMessage.append('.');
				if (noProblems) {
					dco = new DirCacheCheckout(repo,
							headCommit.getTree(), repo.lockDirCache(),
							merger.getResultTreeId());
					dco.setFailOnConflict(true);
					dco.checkout();

					String msg = null;
					RevCommit newHead = null;
					MergeStatus mergeStatus = null;
					if (!squash) {
						newHead = new Git(getRepository()).commit()
							.setReflogComment(refLogMessage.toString()).call();
						mergeStatus = MergeStatus.MERGED;
					} else {
						msg = JGitText.get().squashCommitNotUpdatingHEAD;
						newHead = headCommit;
						mergeStatus = MergeStatus.MERGED_SQUASHED;
					}
					return new MergeResult(newHead.getId(), null,
							new ObjectId[] { headCommit.getId(),
									srcCommit.getId() }, mergeStatus,
							mergeStrategy, null, msg);
				} else {
					if (failingPaths != null) {
						repo.writeMergeCommitMsg(null);
						repo.writeMergeHeads(null);
						return new MergeResult(null,
								merger.getBaseCommit(0, 1),
								new ObjectId[] {
										headCommit.getId(), srcCommit.getId() },
								MergeStatus.FAILED, mergeStrategy,
								lowLevelResults, failingPaths, null);
					} else {
						String mergeMessageWithConflicts = new MergeMessageFormatter()
								.formatWithConflicts(mergeMessage,
										unmergedPaths);
						repo.writeMergeCommitMsg(mergeMessageWithConflicts);
						return new MergeResult(null,
								merger.getBaseCommit(0, 1),
								new ObjectId[] { headCommit.getId(),
										srcCommit.getId() },
								MergeStatus.CONFLICTING, mergeStrategy,
								lowLevelResults, null);
					}
				}
			}
		} catch (org.jboss.forge.jgit.errors.CheckoutConflictException e) {
			List<String> conflicts = (dco == null) ? Collections
					.<String> emptyList() : dco.getConflicts();
			throw new CheckoutConflictException(conflicts, e);
		} catch (IOException e) {
			throw new JGitInternalException(
					MessageFormat.format(
							JGitText.get().exceptionCaughtDuringExecutionOfMergeCommand,
							e), e);
		} finally {
			if (revWalk != null)
				revWalk.release();
		}
	}

	private void updateHead(StringBuilder refLogMessage, ObjectId newHeadId,
			ObjectId oldHeadID) throws IOException,
			ConcurrentRefUpdateException {
		RefUpdate refUpdate = repo.updateRef(Constants.HEAD);
		refUpdate.setNewObjectId(newHeadId);
		refUpdate.setRefLogMessage(refLogMessage.toString(), false);
		refUpdate.setExpectedOldObjectId(oldHeadID);
		Result rc = refUpdate.update();
		switch (rc) {
		case NEW:
		case FAST_FORWARD:
			return;
		case REJECTED:
		case LOCK_FAILURE:
			throw new ConcurrentRefUpdateException(
					JGitText.get().couldNotLockHEAD, refUpdate.getRef(), rc);
		default:
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().updatingRefFailed, Constants.HEAD,
					newHeadId.toString(), rc));
		}
	}

	/**
	 *
	 * @param mergeStrategy
	 *            the {@link MergeStrategy} to be used
	 * @return {@code this}
	 */
	public MergeCommand setStrategy(MergeStrategy mergeStrategy) {
		checkCallable();
		this.mergeStrategy = mergeStrategy;
		return this;
	}

	/**
	 * @param commit
	 *            a reference to a commit which is merged with the current head
	 * @return {@code this}
	 */
	public MergeCommand include(Ref commit) {
		checkCallable();
		commits.add(commit);
		return this;
	}

	/**
	 * @param commit
	 *            the Id of a commit which is merged with the current head
	 * @return {@code this}
	 */
	public MergeCommand include(AnyObjectId commit) {
		return include(commit.getName(), commit);
	}

	/**
	 * @param name
	 *            a name given to the commit
	 * @param commit
	 *            the Id of a commit which is merged with the current head
	 * @return {@code this}
	 */
	public MergeCommand include(String name, AnyObjectId commit) {
		return include(new ObjectIdRef.Unpeeled(Storage.LOOSE, name,
				commit.copy()));
	}

	/**
	 * If <code>true</code>, will prepare the next commit in working tree and
	 * index as if a real merge happened, but do not make the commit or move the
	 * HEAD. Otherwise, perform the merge and commit the result.
	 * <p>
	 * In case the merge was successful but this flag was set to
	 * <code>true</code> a {@link MergeResult} with status
	 * {@link MergeStatus#MERGED_SQUASHED} or
	 * {@link MergeStatus#FAST_FORWARD_SQUASHED} is returned.
	 *
	 * @param squash
	 *            whether to squash commits or not
	 * @return {@code this}
	 * @since 2.0
	 */
	public MergeCommand setSquash(boolean squash) {
		checkCallable();
		this.squash = squash;
		return this;
	}
}

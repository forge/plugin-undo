/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.undo.forge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.enterprise.event.Observes;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.forge.git.GitUtils;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.resources.DirectoryResource;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.shell.events.CommandExecuted;
import org.jboss.forge.test.AbstractShellTest;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jevgeni.zelenkov@gmail.com">Jevgeni Zelenkov</a>
 * 
 */
public class UndoFacetTest extends AbstractShellTest
{
   @Deployment
   public static JavaArchive getDeployment()
   {
      return AbstractShellTest.getDeployment().addPackages(true, UndoPlugin.class.getPackage(),
               UndoFacet.class.getPackage());
   }

   public static Project project = null;

   @Test
   public void shouldInstallPlugin() throws Exception
   {
      Project project = initializeJavaProject();

      Assert.assertNotNull(project);

      getShell().execute("undo setup");

      Git repo = GitUtils.git(project.getProjectRoot());
      Assert.assertNotNull("git is not initialized", repo);

      String undoBranch = project.getFacet(UndoFacet.class).getUndoBranchName();

      boolean containsUndoBranch = false;
      for (Ref branch : GitUtils.getLocalBranches(repo))
      {
         // branchNames contain "refs/heads" prefix by default
         if (branch.getName().endsWith(undoBranch))
         {
            containsUndoBranch = true;
         }
      }

      Assert.assertTrue("should contain undo-branch", containsUndoBranch);
   }

   @Test
   public void shouldInstallPluginWithCustomName() throws Exception
   {
      Project project = initializeJavaProject();

      getShell().execute("undo setup --branchName custom");

      Git repo = GitUtils.git(project.getProjectRoot());
      Assert.assertNotNull("git is not initialized", repo);

      String undoBranch = project.getFacet(UndoFacet.class).getUndoBranchName();

      boolean containsUndoBranch = false;
      for (Ref branch : GitUtils.getLocalBranches(repo))
      {
         // branchNames contain "refs/heads" prefix by default
         if (branch.getName().endsWith(undoBranch))
         {
            containsUndoBranch = true;
         }
      }

      Assert.assertTrue("should contain undo-branch", containsUndoBranch);
   }

   @Test
   public void shouldAddChangesIntoUndoBranch() throws Exception
   {
      UndoFacetTest.project = initializeJavaProject();

      getShell().execute("undo setup");

      Git repo = GitUtils.git(project.getProjectRoot());
      GitUtils.addAll(repo);
      GitUtils.commitAll(repo, "add all commit");

      String filename = "test1.txt";
      String contents = "foo bar baz";

      String forgeUndoPrefix = "history-branch: changes introduced by the ";
      String commandName = "touch";
      String command = commandName + " --filename " + filename + " --contents " + Strings.enquote(contents);

      getShell().execute(command);

      DirectoryResource dir = project.getProjectRoot();
      FileResource<?> file = dir.getChild(filename).reify(FileResource.class);

      Assert.assertTrue("file doesn't exist", file.exists());

      // assert the results of the previous command
      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommits();
      List<String> commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals("wrong number of commits in the history branch", 3, commitMsgs.size());
      Assert.assertEquals("commit messages do not match", forgeUndoPrefix + Strings.enquote(commandName),
               commitMsgs.get(0));
   }

   @Test
   public void shouldUndoLastChange() throws Exception
   {
      // init
      // touch plugin file1
      // verify file1 exists
      // verify commit in history branch exists
      // undo last change
      // verify file1 doesn't exist
      // verify commit in history branch doesn't exist

      UndoFacetTest.project = initializeJavaProject();

      getShell().execute("undo setup");

      Git repo = GitUtils.git(project.getProjectRoot());
      GitUtils.addAll(repo);
      GitUtils.commitAll(repo, "add all commit");

      String filename = "test1.txt";
      String contents = "foo bar baz";

      String forgeUndoPrefix = "history-branch: changes introduced by the ";
      String commandName = "touch";
      String command = commandName + " --filename " + filename + " --contents " + Strings.enquote(contents);

      getShell().execute(command);

      DirectoryResource dir = project.getProjectRoot();
      FileResource<?> file = dir.getChild(filename).reify(FileResource.class);

      Assert.assertTrue("file doesn't exist", file.exists());
      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommits();
      List<String> commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals("wrong number of commits in the history branch", 3, commitMsgs.size());
      Assert.assertEquals("commit messages do not match", forgeUndoPrefix + Strings.enquote(commandName),
               commitMsgs.get(0));

      // restore
      boolean isRestored = project.getFacet(UndoFacet.class).undoLastChange();
      Assert.assertTrue("undo failed", isRestored);

      file = dir.getChild(filename).reify(FileResource.class);

      Assert.assertFalse("file should not exist", file.exists());
      commits = project.getFacet(UndoFacet.class).getStoredCommits();
      commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals("wrong number of commits in the history branch", 2, commitMsgs.size());
      Assert.assertEquals(UndoFacet.UNDO_INSTALL_COMMIT_MSG, commitMsgs.get(0));
   }

   // helper methods
   private List<String> extractCommitMsgs(final Iterable<RevCommit> collection)
   {
      List<String> commitMsgs = new ArrayList<String>();

      Iterator<RevCommit> iter = collection.iterator();
      while (iter.hasNext())
      {
         RevCommit commit = iter.next();
         commitMsgs.add(commit.getFullMessage());
      }

      return commitMsgs;
   }

   // INFO: copypasted from UndoFacet.
   // During tests this method is not called in UndoFacet.
   public void updateHistoryBranch(@Observes CommandExecuted command)
   {
      // ignore if called outside of project
      if (project == null)
         return;

      // not sure in what order CommandExecuted events are fired.
      // we are only interested in touch-command calls for this test.
      if (Strings.areEqual(command.getCommand().getName(), "new-project"))
         return;

      if (Strings.areEqual(command.getCommand().getName(), "setup"))
         return;

      // System.err.println("COMMAND: " + command.getCommand().getName());

      try
      {
         Git repo = GitUtils.git(project.getProjectRoot());
         String oldBranch = GitUtils.getCurrentBranchName(repo);

         String undoBranch = project.getFacet(UndoFacet.class).getUndoBranchName();

         GitUtils.addAll(repo);
         GitUtils.stashCreate(repo);

         Ref historyBranch = GitUtils.switchBranch(repo, undoBranch);

         Assert.assertNotNull(historyBranch);
         Assert.assertEquals("failed to switch to the history branch", historyBranch.getObjectId(),
                  project.getFacet(UndoFacet.class).getUndoBranchRef().getObjectId());

         GitUtils.stashApply(repo);

         GitUtils.commitAll(repo,
                  "history-branch: changes introduced by the " + Strings.enquote(command.getCommand().getName()));

         GitUtils.switchBranch(repo, oldBranch);

         GitUtils.stashApply(repo);
         GitUtils.stashDrop(repo);
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
      catch (GitAPIException e)
      {
         e.printStackTrace();
      }
   }

}

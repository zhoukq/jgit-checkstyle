# jgit-checkstyle
use checkstyle implement incremental check
with the implement, we can only use checkstyle check the in code in every pull request

main code is clone from https://github.com/centic9/jgit-cookbook

1. git workflow(use jgit)
	git merge-base xxx xxx //get common commit. 
	git rev-parse HEAD //get current commit  
	git diff --unified=0  commit1 commit2 //get diff
2. parse diff
3. update suppressions.xml
4. combine with checkstyleMain task 

please see code in DiffFilesInCommit.java and build.gradle


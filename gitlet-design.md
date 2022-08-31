# Gitlet Design Document

Luisa Wang:

## Classes and Data Structures

### Commit

#### Fields
All fields are private 
1. Message
2. Date
3. Parent (sha1 hash (String). "init" if it is the first)
4. Blobs (A file of a HashMap mapping file names to ArrayList containing
   information about their sha1, file path, and the file path of 
   the "snapshot" ("snapshot" files are stored in PAST_FILES)).
   
6. second parent (String sha1 of the second parent if there is one. default null).

####methods

1. Commit() (creates first commit).
2. Commit(args) (creates other commit instances)
3. parent() (prints parent commit sha1)
4. printDate() (prints the date of commit)
5. committedFiles() (returns blobs hashmap)
7. getDate() (returns the Date)
8. setSecondParent(arg) (sets the second parent to the input of the method)
9. getSecondParent() (returns the second parent)



### Utils

#### Fields

provided class.
exitWithError method was added. It is based off lab 6

###Main
####Fields
provided class that uses Repository.
Two methods were created in this class:
1. validateNumArgs()- the same as lab 6
2. inGitletDir() - checks to make sure gitlet init has been run


## Algorithms
all the ones in the spec +
1. deserialization methods so that I do not need to rewrite it over and over
2. Two large helper methods for checkout and merge. checkout helper is used in reset and checkout branches. It
takes in deserialized data structures. merge helper has three loops, going over all
   three commits. One loop focuses on removal while the others can handle conflicts. Only the
   first one checks out files.
   
3. identicalfileremover for add. It makes sure that files that are identical to
the ones in the previous commit are not added
   
4. updateancestors for merge. It updates commit ancestor file after a successful merge with
the new split point created by the merge. The current branch also gets the split points
   of the other branch's previous split points.
   
5. sortbyvalues for helping find split points. sorts Linked Hash map by its date
values. more info in persistence section
   
6. splitpoint finds the split point by looping through all split points and checking if the 
other branch has that split point as well
   
7. stageclear empties both stages.
8. abbreviatedsha1 finds the non abbreviated sha1 when given an abbreviated one. otherwise,
will check to see if the sha1 hash exists.
   
9. printmodified and printuntracked are for the extra credit and handle all cases
10. untrackedexists returns true if there is an untracked file. Loops through all the
files in CWD.
    
11. committofile creates a file of the commit provided using writeObject

## Persistence
#### data structure
1. StagingAA (File of HashMap. Info on Files to be committed. 
   Same structure as blobs in Commit class)
   
2. mainTree (File of TreeMap. Maps sha1 hash to Commit file path)
3. branches (LinkedHashMap of branch names mapped to the 
   sha1 hash it points to)
4. currentBranch (txt File of the current branch's name)
5. PAST_FILES (directory of all added files)
6. StagingAR same as StagingAA, but for remove
7. CommitAncestors (A hashmap of a linked hash map. the hashmap
   stores branch names mapped to a linked hash map of all the 
   branch's split points. A split point is added when a branch is
   created OR a successful merge occurs. The linked hash map maps the
   split points to the date that commit was created.
   This allows it to be ordered by date. The dates are values and 
   not keys to account for the possibility that two commits are done 
   at the same time. At the beginning of the merge
   method, the linked hash map will be ordered by its date values
   so that it is possible to find the correct split point for the 
   scenario)
   * this does not use a treeMap because of serialization issues when using the comparator 
   
#### Persistence using above strucutres
8. init: sets up the persistence with .gitlet
9. add: creates a new file in PAST_FILES that contains the data of the 
   given file
   
10. remove: the files in PAST_FILES are not touched, this just takes away the link to
    them in the recent commit/stage add under certain conditions
10. Commit: creates a file that stores the paths and other important details
of the relevant files in PAST_FILES.
    
12. mainTree, branches, currentBranch, and commitAncestors are used to find a specific 
commit. The first three almost always work together in the order of 
    currentBranch (get branch name)-> branches (get branch's sha1 hash)
    -> mainTree (get commit file path). 




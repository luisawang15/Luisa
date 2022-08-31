package gitlet;


import java.io.File;
import java.util.*;

import static gitlet.Utils.*;

/** Represents a gitlet repository.
 * Stores past file data, making it possible to obtain past versions of files
 * in case something goes wrong. Makes it easier to edit without worrying that
 * past versions will disappear.
 *
 *  @author Luisa Wang
 */
public class Repository {

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));

    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");

    /** commit folder, stores commits and mainTree */
    public static final File COMMIT_FOLDER = join(GITLET_DIR, "commitFolder");

    /** file data of all the added versions. (blob data). Makes it possible to
     * get old version of file */
    public static final File PAST_FILES = join(COMMIT_FOLDER, "past file data");

    /** staging area for add, file containing HashMap < file name, Array List (string) of
     *  file's sha1 and stored filepath (data of old versions) > */
    private static final File STAGINGAA = join(GITLET_DIR, "StagingAreaAdd");

    /** staging area for remove, file containing HashMap < file name, Array List (string) of
     * file's sha1 and stored filepath (data of old versions) > */
    private static final File STAGINGAR = join(GITLET_DIR, "StagingAreaRemove");

    /** file of treeMap < sha1 hash of commits, commit file path (string) >  */
    private static final File MAINTREE = join(GITLET_DIR, "mainTree");

    /** current branch, stores branch file name. Can be used to find the sha1
     * of the commit corresponding to it (BRANCHES). ie master */
    private static final File CURRENTBRANCH = join(GITLET_DIR, "currentBranch.txt");

    /** A file of a LinkedHashMap < branch name, sha 1 of commit it points to > */
    private static final File BRANCHES = join(GITLET_DIR, "branches");

    /** A file of a HashMap < branchName, Linked Hash Map of
     * split points (strings), Date >. Used to find split point in merge.
     * Gets ordered by date at the beginning of each merge generally */
    private static final File COMMITANCESTORS = join(GITLET_DIR, "linked commit ancestors");

    /** hashmap of remote name and its remote directory path */
    private static final File REMOTE = join(GITLET_DIR, "remote");


    public static void init() {
        if (GITLET_DIR.exists()) {
            exitWithError("A Gitlet version-control system already "
                    + "exists in the current directory.");
        }
        GITLET_DIR.mkdir();
        COMMIT_FOLDER.mkdir();
        PAST_FILES.mkdir();
        Commit c = new Commit();
        File f = commitToFile(c, "init");
        String sha = sha1(readContents(f));
        f.renameTo(new File(COMMIT_FOLDER + "/" + sha));
        writeContents(CURRENTBRANCH, "master");
        TreeMap<String, String> mainT = new TreeMap<>();
        mainT.put(sha, COMMIT_FOLDER + "/" + sha);
        writeObject(MAINTREE, mainT);
        writeObject(STAGINGAA, new HashMap<String, ArrayList<String>>());
        writeObject(STAGINGAR, new HashMap<String, ArrayList<String>>());
        LinkedHashMap<String, String> b = new LinkedHashMap<>();
        b.put("master", sha);
        writeObject(BRANCHES, b);
        HashMap<String, LinkedHashMap<String, Date>> a = new HashMap<>();
        LinkedHashMap<String, Date> ml = new LinkedHashMap<>();
        ml.put(sha, c.getDate());
        a.put("master", ml);
        writeObject(COMMITANCESTORS, a);
        writeObject(REMOTE, new HashMap<String, String>());
    }

    public static void status() {
        String deserializedCurrentName = readContentsAsString(CURRENTBRANCH);
        LinkedHashMap<String, String> deserializedBranch = deserializeBranch();
        HashMap<String, ArrayList<String>> deserializedRemove = deserializeStageRemove();
        HashMap<String, ArrayList<String>> deserializedStageAdd = deserializeStageAdd();
        TreeMap<String, String> deserializedMainT = deserializeMain();
        Commit currentCommit = readObject(new File(deserializedMainT.get(
                deserializedBranch.get(deserializedCurrentName))), Commit.class);
        System.out.println("=== Branches ===");
        for (String key : deserializedBranch.keySet()) {
            if (key.equals(deserializedCurrentName)) {
                System.out.print("*");
            }
            System.out.println(key);
        }
        System.out.println();
        System.out.println("=== Staged Files ===");
        for (String key : deserializedStageAdd.keySet()) {
            System.out.println(key);
        }
        System.out.println();
        System.out.println("=== Removed Files ===");
        for (String key : deserializedRemove.keySet()) {
            System.out.println(key);
        }
        System.out.println();
        System.out.println("=== Modifications Not Staged For Commit ===");
        printModified(currentCommit, deserializedStageAdd, deserializedRemove);
        System.out.println();
        System.out.println("=== Untracked Files ===");
        printUntrackedFiles(currentCommit, deserializedStageAdd, deserializedRemove);
    }

    public static void add(String fileName) {
        File file = join(CWD, fileName);
        if (!file.exists()) {
            exitWithError("File does not exist.");
        }
        String sha1 = sha1(readContents(file));
        HashMap<String, ArrayList<String>> deserializedStage = deserializeStageAdd();
        if (!identicalfileremover(sha1, fileName, deserializedStage)) {
            File copy = join(PAST_FILES, sha1);
            writeContents(copy, readContents(file));
            ArrayList<String> L = new ArrayList<>();
            L.add(sha1);
            L.add(copy.toString());
            deserializedStage.put(fileName, L);
        }
        writeObject(STAGINGAA, deserializedStage);
        HashMap<String, ArrayList<String>> deserializedStageRemove = deserializeStageRemove();
        deserializedStageRemove.remove(fileName);
        writeObject(STAGINGAR, deserializedStageRemove);
    }

    /** Makes sure files identical its state in current commit don't get added */
    private static boolean identicalfileremover(String sha,
                                                String fileName,
                                                HashMap<String, ArrayList<String>> area) {
        TreeMap<String, String> deserializedMainT = deserializeMain();
        LinkedHashMap<String, String> deserializedBranch = deserializeBranch();
        String deserializedCurrentSha = deserializedBranch.get(readContentsAsString(CURRENTBRANCH));
        Commit currentCommit = readObject(new File(deserializedMainT.get(
                deserializedCurrentSha)), Commit.class);
        if (currentCommit.committedFiles().containsKey(fileName)) {
            if (currentCommit.committedFiles().get(fileName).get(0).equals(sha)) {
                area.remove(fileName);
                return true;
            }
        }
        return false;
    }

    public static void rm(String fileName) {
        HashMap<String, ArrayList<String>> deserializedStageRemove = deserializeStageRemove();
        TreeMap<String, String> deserializedMainT = deserializeMain();
        HashMap<String, ArrayList<String>> deserializedStageAdd = deserializeStageAdd();
        LinkedHashMap<String, String> deserializedBranch = deserializeBranch();
        String deserializedCurrent = deserializedBranch.get(readContentsAsString(CURRENTBRANCH));
        Commit deserializedParent = readObject(new File(deserializedMainT.get(
                deserializedCurrent)), Commit.class);
        boolean stageAddContains = deserializedStageAdd.containsKey(fileName);
        boolean parentContains = deserializedParent.committedFiles().containsKey(fileName);
        if (!stageAddContains && !parentContains) {
            exitWithError("No reason to remove the file.");
        }
        if (parentContains) {
            deserializedStageRemove.put(fileName,
                    deserializedParent.committedFiles().get(fileName));
            writeObject(STAGINGAR, deserializedStageRemove);
            File file = join(CWD, fileName);
            file.delete();
        }
        if (stageAddContains) {
            deserializedStageAdd.remove(fileName);
            writeObject(STAGINGAA, deserializedStageAdd);
        }
    }

    public static void makeCommit(String message, String secondParentSha, String secondP) {
        TreeMap<String, String> deserializedMainT = deserializeMain();
        LinkedHashMap<String, String> deserializedBranch = deserializeBranch();
        String deserializedCurrentFileName = readContentsAsString(CURRENTBRANCH);
        String deserializedCurrent = deserializedBranch.get(deserializedCurrentFileName);
        Commit deserializedParent = readObject(new File(deserializedMainT.get(
                deserializedCurrent)), Commit.class);
        HashMap<String, ArrayList<String>> deserializedStageAdd = deserializeStageAdd();
        HashMap<String, ArrayList<String>> deserializedStageRemove = deserializeStageRemove();
        if (deserializedStageRemove.isEmpty() && deserializedStageAdd.isEmpty()) {
            exitWithError("No changes added to the commit.");
        }
        Commit c = new Commit(message, deserializedStageAdd, deserializedStageRemove,
                deserializedCurrent, deserializedParent);
        c.setSecondParent(secondParentSha);
        stageClear(deserializedStageAdd, deserializedStageRemove);
        File f = commitToFile(c, message);
        String sha = sha1(readContents(f));
        f.renameTo(new File(COMMIT_FOLDER + "/" + sha));
        HashMap<String, LinkedHashMap<String, Date>> deserializedAncestors = deserializeAncestors();
        LinkedHashMap<String, Date> l = deserializedAncestors.get(deserializedCurrentFileName);
        l.put(sha, c.getDate());
        if (secondParentSha != null) {
            for (Map.Entry<String, Date> entry
                    : deserializedAncestors.get(secondP).entrySet()) {
                l.put(entry.getKey(), entry.getValue());
            }
        }
        writeObject(COMMITANCESTORS, deserializedAncestors);
        deserializedBranch.put(deserializedCurrentFileName, sha);
        deserializedMainT.put(sha, COMMIT_FOLDER + "/" + sha);
        writeObject(BRANCHES, deserializedBranch);
        writeObject(MAINTREE, deserializedMainT);
    }

    public static void branch(String branchName) {
        LinkedHashMap<String, String> deserializedBranches = deserializeBranch();
        if (deserializedBranches.containsKey(branchName)) {
            exitWithError("A branch with that name already exists.");
        }
        String deserializedCurrent = readContentsAsString(CURRENTBRANCH);
        String deserializedCurrentSha = deserializedBranches.get(deserializedCurrent);
        deserializedBranches.put(branchName, deserializedCurrentSha);
        writeObject(BRANCHES, deserializedBranches);
        HashMap<String, LinkedHashMap<String, Date>> deserializedAncestors = deserializeAncestors();
        LinkedHashMap<String, Date> l = new LinkedHashMap<>();
        for (Map.Entry<String, Date> entry
                : deserializedAncestors.get(deserializedCurrent).entrySet()) {
            l.put(entry.getKey(), entry.getValue());
        }
        deserializedAncestors.put(branchName, l);
        writeObject(COMMITANCESTORS, deserializedAncestors);
    }

    public static void rmBranch(String branchName) {
        LinkedHashMap<String, String> deserializedBranch = deserializeBranch();
        String deserializedCurrent = readContentsAsString(CURRENTBRANCH);
        if (!deserializedBranch.containsKey(branchName)) {
            exitWithError("A branch with that name does not exist.");
        }
        if (deserializedCurrent.equals(branchName)) {
            exitWithError("Cannot remove the current branch.");
        }
        deserializedBranch.remove(branchName);
        writeObject(BRANCHES, deserializedBranch);
        HashMap<String, LinkedHashMap<String, Date>> deserializedAncestors = deserializeAncestors();
        deserializedAncestors.remove(branchName);
        writeObject(COMMITANCESTORS, deserializedAncestors);
    }

    public static void log() {
        LinkedHashMap<String, String> deserializedBranch = deserializeBranch();
        String deserializedCurrentName = readContentsAsString(CURRENTBRANCH);
        String sha1 = deserializedBranch.get(deserializedCurrentName);
        TreeMap<String, String> deserializedMainT = deserializeMain();
        Commit c = readObject(new File(deserializedMainT.get(sha1)), Commit.class);
        while (true) {
            System.out.println("===");
            System.out.println("commit " + sha1);
            if (c.getSecondParent() != null) {
                System.out.println("Merge: " + c.parent().substring(0, 7)
                        + " " + c.getSecondParent().substring(0, 7));
            }
            c.printDate();
            c.printMessage();
            System.out.println();
            sha1 = c.parent();
            if (sha1.equals("init")) {
                break;
            }
            File nextCommit = new File(deserializedMainT.get(sha1));
            c = readObject(nextCommit, Commit.class);
        }

    }

    /** log of all commits */
    public static void globalLog() {
        List<String> commits = plainFilenamesIn(COMMIT_FOLDER);
        for (int i = 0; i < commits.size(); i += 1) {
            System.out.println("===");
            System.out.println("commit " + commits.get(i));
            Commit c = readObject(new File(COMMIT_FOLDER + "/"
                    + commits.get(i)), Commit.class);
            if (c.getSecondParent() != null) {
                System.out.println("Merge: " + c.parent().substring(0, 7)
                        + " " + c.getSecondParent().substring(0, 7));
            }
            c.printDate();
            c.printMessage();
            System.out.println();
        }
    }

    /** prints ids of all commits that have the given commit message.
     * If there are multiple, prints ids on separate line */
    public static void find(String commitMessage) {
        List<String> commits = plainFilenamesIn(COMMIT_FOLDER);
        boolean doesNotExist = true;
        for (int i = 0; i < commits.size(); i += 1) {
            Commit c = readObject(new File(COMMIT_FOLDER
                    + "/" + commits.get(i)), Commit.class);
            if (c.getMessage().equals(commitMessage)) {
                System.out.println(commits.get(i));
                doesNotExist = false;
            }
        }
        if (doesNotExist) {
            exitWithError("Found no commit with that message.");
        }
    }

    /** first and second checkout method, commit iD's files */
    public static void checkout(String commitID, String fileName) {
        TreeMap<String, String> deserializedMainT = deserializeMain();
        LinkedHashMap<String, String> deserializedBranch = deserializeBranch();
        String deserializedCurrentName = readContentsAsString(CURRENTBRANCH);
        String deserializedComID;
        if (commitID.equals("current")) {
            deserializedComID = deserializedBranch.get(deserializedCurrentName);
        } else {
            deserializedComID = abbreviatedSha1(commitID, deserializedMainT);
        }
        if (deserializedComID == null) {
            exitWithError("No commit with that id exists.");
        }
        Commit deserializedParent = readObject(new File(
                deserializedMainT.get(deserializedComID)), Commit.class);
        if (!deserializedParent.committedFiles().containsKey(fileName)) {
            exitWithError("File does not exist in that commit.");
        }
        ArrayList<String> a = deserializedParent.committedFiles().get(fileName);
        File f = join(CWD, fileName);
        File oldContent = new File(a.get(1));
        writeContents(f, readContents(oldContent));
    }

    /** third checkout method, with branches */
    public static void checkout(String branchName) {
        LinkedHashMap<String, String> deserializedBranches = deserializeBranch();
        String currentBranchName = readContentsAsString(CURRENTBRANCH);
        if (!deserializedBranches.containsKey(branchName)) {
            exitWithError("No such branch exists.");
        }
        if (currentBranchName.equals(branchName)) {
            exitWithError("No need to checkout the current branch.");
        }
        HashMap<String, ArrayList<String>> deserializedStageAdd = deserializeStageAdd();
        TreeMap<String, String> deserializedMainT = deserializeMain();
        String commitSha1 = deserializedBranches.get(branchName);
        Commit deserializedParent = readObject(new File(
                deserializedMainT.get(commitSha1)), Commit.class);
        Commit currentCommit = readObject(new File(deserializedMainT.get(
                deserializedBranches.get(currentBranchName))), Commit.class);
        HashMap<String, ArrayList<String>> deserializedStageRemove = deserializeStageRemove();
        checkoutHelper(deserializedParent, deserializedStageAdd,
                deserializedBranches, branchName, currentCommit, null);
        stageClear(deserializedStageAdd, deserializedStageRemove);
    }

    public static void reset(String commitID) {
        TreeMap<String, String> deserializedMainT = deserializeMain();
        String deserializedComID = abbreviatedSha1(commitID, deserializedMainT);
        if (deserializedComID == null) {
            exitWithError("No commit with that id exists.");
        }
        String currentBranchName = readContentsAsString(CURRENTBRANCH);
        LinkedHashMap<String, String> deserializedBranches = deserializeBranch();
        HashMap<String, ArrayList<String>> deserializedStageAdd = deserializeStageAdd();
        HashMap<String, ArrayList<String>> deserializedStageRemove = deserializeStageRemove();
        Commit deserializedParent = readObject(new File(deserializedMainT.get(
                deserializedComID)), Commit.class);
        Commit currentCommit = readObject(new File(deserializedMainT.get(
                deserializedBranches.get(currentBranchName))), Commit.class);
        checkoutHelper(deserializedParent, deserializedStageAdd, deserializedBranches,
                currentBranchName, currentCommit, deserializedComID);
        stageClear(deserializedStageAdd, deserializedStageRemove);
        HashMap<String, LinkedHashMap<String, Date>>
                deserializedAncestor = deserializeAncestors();
        if (!deserializedAncestor.get(currentBranchName).containsKey(commitID)) {
            for (String bName : deserializedBranches.keySet()) {
                if (deserializedAncestor.get(bName).containsKey(commitID)) {
                    deserializedAncestor.get(currentBranchName).clear();
                    for (Map.Entry<String, Date> entry
                            : deserializedAncestor.get(bName).entrySet()) {
                        deserializedAncestor.get(currentBranchName).put(
                                entry.getKey(), entry.getValue());
                    }
                    break;
                }
            }
            writeObject(COMMITANCESTORS, deserializedAncestor);
        }
    }

    public static void merge(String branchName) {
        HashMap<String, ArrayList<String>> deserializedStageRemove = deserializeStageRemove();
        HashMap<String, ArrayList<String>> deserializedStageAdd = deserializeStageAdd();
        if (!deserializedStageAdd.isEmpty() || !deserializedStageRemove.isEmpty()) {
            exitWithError("You have uncommitted changes.");
        }
        LinkedHashMap<String, String> deserializedBranches = deserializeBranch();
        if (!deserializedBranches.containsKey(branchName)) {
            exitWithError("A branch with that name does not exist.");
        }
        TreeMap<String, String> deserializedMainT = deserializeMain();
        String currentBranchName = readContentsAsString(CURRENTBRANCH);
        if (currentBranchName.equals(branchName)) {
            exitWithError("Cannot merge a branch with itself.");
        }
        String otherSha1 = deserializedBranches.get(branchName);
        String currentSha1 = deserializedBranches.get(currentBranchName);
        Commit otherBranch = readObject(new File(
                deserializedMainT.get(otherSha1)), Commit.class);
        Commit currentCommit = readObject(new File(
                deserializedMainT.get(currentSha1)), Commit.class);
        List<String> filesInCWD = plainFilenamesIn(CWD);
        if (untrackedExists(otherBranch, currentCommit, deserializedStageAdd, filesInCWD)) {
            exitWithError("There is an untracked file in the way; "
                    + "delete it, or add and commit it first.");
        }
        HashMap<String, LinkedHashMap<String, Date>> deserializedAncestors = deserializeAncestors();
        deserializedAncestors.put(branchName, (LinkedHashMap<String, Date>)
                sortByValues(deserializedAncestors.get(branchName)));
        deserializedAncestors.put(currentBranchName, (LinkedHashMap<String, Date>)
                sortByValues(deserializedAncestors.get(currentBranchName)));
        String latestCommonAncestorSha = splitPoint(deserializedAncestors.get(branchName),
                deserializedAncestors.get(currentBranchName));
        Commit commonAncestor =
                readObject(new File(deserializedMainT.get(latestCommonAncestorSha)), Commit.class);
        if (otherSha1.equals(latestCommonAncestorSha)) {
            exitWithError("Given branch is an ancestor of the current branch.");
        }
        if (currentSha1.equals(latestCommonAncestorSha)) {
            checkoutHelper(otherBranch, deserializedStageAdd, deserializedBranches,
                    branchName, currentCommit, null);
            stageClear(deserializedStageAdd, deserializedStageRemove);
            exitWithError("Current branch fast-forwarded.");
        }
        if (fileCheckInMerge(otherBranch, commonAncestor, currentCommit, otherSha1)) {
            System.out.println("Encountered a merge conflict.");
        }
        makeCommit("Merged " + branchName
                + " into " + currentBranchName + ".", otherSha1, branchName);
        updateAncestors(deserializedAncestors, currentBranchName,
                branchName, otherSha1, otherBranch);
    }

    public static void addremote(String name, String remoteDirectory) {
        remoteDirectory = remoteDirectory.replace("/", File.separator);
        @SuppressWarnings("unchecked")
        HashMap<String, String> deserializedRemote = readObject(REMOTE, HashMap.class);
        if (deserializedRemote.containsKey(name)) {
            exitWithError("A remote with that name already exists.");
        }
        deserializedRemote.put(name, remoteDirectory);
        writeObject(REMOTE, deserializedRemote);
    }

    public static void rmremote(String name) {
        @SuppressWarnings("unchecked")
        HashMap<String, String> deserializedRemote = readObject(REMOTE, HashMap.class);
        if (!deserializedRemote.containsKey(name)) {
            exitWithError("A remote with that name does not exist.");
        }
        deserializedRemote.remove(name);
        writeObject(REMOTE, deserializedRemote);
    }

    public static void push(String name, String remoteBranchName) {
        @SuppressWarnings("unchecked")
        HashMap<String, String> deserializedRemote = readObject(REMOTE, HashMap.class);
        File f = new File(deserializedRemote.get(name));
        if (!f.exists()) {
            exitWithError("Remote directory not found.");
        }
        HashMap<String, LinkedHashMap<String, Date>> deserializedAncestor =
                deserializeAncestors();
        String deserializedCurrentName = readContentsAsString(CURRENTBRANCH);
        File remoteBranches = join(f, "branches");
        File remoteMainT = join(f, "mainTree");
        File remoteAncestors = join(f, "linked commit ancestors");
        File remoteCurrent = join(f, "currentBranch.txt");
        File remoteCWD = new File(deserializedRemote.get(name)
                .replace(File.separator + ".gitlet", ""));
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, String> dRemoteBranches =
                readObject(remoteBranches, LinkedHashMap.class);
        @SuppressWarnings("unchecked")
        TreeMap<String, String> dRemoteMain = readObject(remoteMainT, TreeMap.class);
        @SuppressWarnings("unchecked")
        HashMap<String, LinkedHashMap<String, Date>> dRemoteAncestors =
                readObject(remoteAncestors, HashMap.class);
        String dRemoteCurrent = readContentsAsString(remoteCurrent);
        if (!dRemoteBranches.containsKey(remoteBranchName)) {
            dRemoteBranches.put(remoteBranchName, dRemoteBranches.get(dRemoteCurrent));
            LinkedHashMap<String, Date> l = new LinkedHashMap<>();
            for (Map.Entry<String, Date> entry
                    : dRemoteAncestors.get(dRemoteCurrent).entrySet()) {
                l.put(entry.getKey(), entry.getValue());
            }
            dRemoteAncestors.put(remoteBranchName, l);
        }
        String sharedSha = dRemoteBranches.get(remoteBranchName);
        if (!deserializedAncestor.get
                (deserializedCurrentName).containsKey(sharedSha)) {
            exitWithError("Please pull down remote changes before pushing.");
        }
        deserializedAncestor.put(deserializedCurrentName, (LinkedHashMap<String, Date>)
                sortByValues(deserializedAncestor.get(deserializedCurrentName)));
        TreeMap<String, String> deserializedMain = deserializeMain();
        for (Map.Entry<String, Date> entry
                : deserializedAncestor.get(deserializedCurrentName).entrySet()) {
            if (entry.getKey().equals(sharedSha)) {
                break;
            }
            dRemoteAncestors.get(remoteBranchName).put(entry.getKey(),
                    new Date(entry.getValue().getTime()));
            dRemoteMain.put(entry.getKey(), deserializedMain.get(entry.getKey()));
        }
        HashMap<String, String> deserializedBranch = deserializeBranch();
        dRemoteBranches.put(remoteBranchName,
                deserializedBranch.get(deserializedCurrentName));
        writeContents(remoteCurrent, deserializedCurrentName);
        writeObject(remoteBranches, dRemoteBranches);
        writeObject(remoteAncestors, deserializedAncestor);
        writeObject(remoteMainT, dRemoteMain);
    }

    public static void fetch(String remoteName, String remoteBranchName) {
        @SuppressWarnings("unchecked")
        HashMap<String, String> deserializedRemote = readObject(REMOTE, HashMap.class);
        File f = new File(deserializedRemote.get(remoteName));
        if (!f.exists()) {
            exitWithError("Remote directory not found.");
        }
        File remoteAncestors = join(f, "linked commit ancestors");
        @SuppressWarnings("unchecked")
        HashMap<String, LinkedHashMap<String, Date>>
                dRemoteAncestors = readObject(remoteAncestors, HashMap.class);
        if (!dRemoteAncestors.containsKey(remoteBranchName)) {
            exitWithError("That remote does not have that branch.");
        }
        File remoteMainT = join(f, "mainTree");
        File remoteBranches = join(f, "branches");
        @SuppressWarnings("unchecked")
        TreeMap<String, String> dRemoteMain = readObject(remoteMainT, TreeMap.class);
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, String> dRemoteBranches =
                readObject(remoteBranches, LinkedHashMap.class);
        TreeMap<String, String> deserializedMainT = deserializeMain();
        LinkedHashMap<String, String> deserializedBranches = deserializeBranch();
        if (!deserializedBranches.containsKey(
                remoteName + "/" + remoteBranchName)) {
            branch(remoteName + "/" + remoteBranchName);
        }
        HashMap<String, LinkedHashMap<String, Date>>
                deserializedAncestors = deserializeAncestors();
        for (Map.Entry<String, Date> entry
                : dRemoteAncestors.get(remoteBranchName).entrySet()) {
            if (!deserializedAncestors.get(remoteName + "/" + remoteBranchName)
                    .containsKey(entry.getKey())) {
                deserializedAncestors.get(remoteName + "/" + remoteBranchName).put(
                        entry.getKey(), new Date(entry.getValue().getTime()));
                deserializedMainT.put(entry.getKey(), dRemoteMain.get(entry.getKey()));
            }
        }
        deserializedBranches.put(remoteName + "/" + remoteBranchName,
                dRemoteBranches.get(remoteBranchName));
        writeObject(BRANCHES, deserializedBranches);
        writeObject(MAINTREE, deserializedMainT);
        writeObject(COMMITANCESTORS, deserializedAncestors);
    }

    public static void pull(String name, String remoteBranchName) {
        fetch(name, remoteBranchName);
        merge(name + "/" + remoteBranchName);
    }

    /** checks files in three commits and decides what to do
     * in each scenario
     * @return true if merge conflict occurred, false otherwise */
    private static boolean fileCheckInMerge(Commit otherBranch, Commit commonAncestor,
                                            Commit currentCommit, String otherSha1) {
        boolean mergeConflict = false;
        HashSet<String> checkedInCurrent = new HashSet<>();
        for (Map.Entry<String, ArrayList<String>> entry
                : otherBranch.committedFiles().entrySet()) {
            boolean ancestorContains = commonAncestor.committedFiles().containsKey(entry.getKey());
            boolean currentContains = currentCommit.committedFiles().containsKey(entry.getKey());
            String currentFileSha = "";
            String ancestorSha = "";
            if (currentContains) {
                currentFileSha = currentCommit.committedFiles().get(
                        entry.getKey()).get(0);
                checkedInCurrent.add(entry.getKey());
            }
            if (ancestorContains) {
                ancestorSha = commonAncestor.committedFiles().get(entry.getKey()).get(0);
            }
            String otherFileSha = entry.getValue().get(0);
            if ((ancestorContains && !currentContains && !otherFileSha.equals(ancestorSha))
                    || (!otherFileSha.equals(currentFileSha) && !otherFileSha.equals(ancestorSha)
                    && !currentFileSha.equals(ancestorSha) && currentContains)) {
                File f = join(CWD, entry.getKey());
                String currentContent = "";
                if (currentContains) {
                    File current =
                            new File(currentCommit.committedFiles().get(entry.getKey()).get(1));
                    currentContent = readContentsAsString(current);
                }
                writeContents(f, "<<<<<<< HEAD\n" + currentContent + "=======\n"
                        + readContentsAsString(new File(entry.getValue().get(1))) + ">>>>>>>\n");
                mergeConflict = true;
                add(entry.getKey());
            } else if ((!ancestorContains && !currentContains) || (!otherFileSha.equals(ancestorSha)
                    && ancestorContains && currentFileSha.equals(ancestorSha))) {
                checkout(otherSha1, entry.getKey());
                add(entry.getKey());
            }
        }
        for (Map.Entry<String, ArrayList<String>> entry
                : commonAncestor.committedFiles().entrySet()) {
            boolean otherContains = otherBranch.committedFiles().containsKey(entry.getKey());
            boolean currentContains = currentCommit.committedFiles().containsKey(entry.getKey());
            String currentFileSha = "";
            if (currentContains) {
                currentFileSha = currentCommit.committedFiles().get(entry.getKey()).get(0);
            }
            String ancestorSha = commonAncestor.committedFiles().get(entry.getKey()).get(0);
            if (currentContains && currentFileSha.equals(ancestorSha) && !otherContains) {
                rm(entry.getKey());
            }
        }
        for (Map.Entry<String, ArrayList<String>> entry
                : currentCommit.committedFiles().entrySet()) {
            if (!checkedInCurrent.contains(entry.getKey())) {
                boolean otherContains = otherBranch.committedFiles().containsKey(entry.getKey());
                String ancestorSha = "";
                boolean ancestorContains = commonAncestor.committedFiles().containsKey(
                        entry.getKey());
                if (ancestorContains) {
                    ancestorSha = commonAncestor.committedFiles().get(entry.getKey()).get(0);
                }
                if (!otherContains && !entry.getValue().get(0).equals(ancestorSha)
                        && ancestorContains) {
                    mergeConflict = true;
                    File f = join(CWD, entry.getKey());
                    writeContents(f, "<<<<<<< HEAD\n" + readContentsAsString(f)
                            + "=======\n" + ">>>>>>>\n");
                    add(entry.getKey());
                }
            }
        }
        return mergeConflict;
    }

    /** update's a branch's ancestors after a merge */
    private static void updateAncestors(HashMap<String, LinkedHashMap<String, Date>>
                                                deserializedAncestors, String currentBranchName,
                                        String branchName, String otherSha1, Commit otherBranch) {
        for (Map.Entry<String, Date> entry : deserializedAncestors.get(branchName).entrySet()) {
            if (!deserializedAncestors.get(currentBranchName).containsKey(entry.getKey())) {
                deserializedAncestors.get(currentBranchName).put(entry.getKey(), entry.getValue());
            }
        }
        deserializedAncestors.get(branchName).put(otherSha1, otherBranch.getDate());
        deserializedAncestors.get(currentBranchName).put(otherSha1, otherBranch.getDate());
        writeObject(COMMITANCESTORS, deserializedAncestors);
    }

    /** comparator for sorting ancestors by date values (backwards)
     * @Source GeeksforGeeks and Techiedelight */
    private static Map<String, Date> sortByValues(Map<String, Date> map) {
        List<Map.Entry<String, Date>> mappings = new ArrayList<>(map.entrySet());
        Collections.sort(mappings, new Comparator<Map.Entry<String, Date>>() {
            public int compare(Map.Entry<String, Date> entry1, Map.Entry<String, Date> entry2) {
                return entry2.getValue().compareTo(entry1.getValue());
            }
        });
        Map<String, Date> linkedHashMap = new LinkedHashMap<>();
        for (Map.Entry<String, Date> entry: mappings) {
            linkedHashMap.put(entry.getKey(), entry.getValue());
        }
        return linkedHashMap;
    }

    /** finds the split point's sha1 for merge */
    private static String splitPoint(Map<String, Date> branchOne, Map<String, Date> branchTwo) {
        for (String sha : branchOne.keySet()) {
            if (branchTwo.containsKey(sha)) {
                return sha;
            }
        }
        return null;
    }

    private static void checkoutHelper(Commit deserializedParent, HashMap<String, ArrayList<String>>
            deserializedStageAdd, LinkedHashMap<String, String> deserializedBranches,
                                       String b, Commit currentCommit, String deserializedComID) {
        List<String> filesInCWD = plainFilenamesIn(CWD);
        if (untrackedExists(deserializedParent, currentCommit, deserializedStageAdd, filesInCWD)) {
            exitWithError("There is an untracked file in the way; "
                    + "delete it, or add and commit it first.");
        }
        for (Map.Entry<String, ArrayList<String>> entry
                : deserializedParent.committedFiles().entrySet()) {
            File f = join(CWD, entry.getKey());
            File oldContent = new File(entry.getValue().get(1));
            writeContents(f, readContents(oldContent));
        }
        for (String f : filesInCWD) {
            if (!deserializedParent.committedFiles().containsKey(f)
                    && currentCommit.committedFiles().containsKey(f)) {
                File file = join(CWD, f);
                file.delete();
            }
        }
        if (deserializedComID != null) {
            deserializedBranches.put(b, deserializedComID);
            writeObject(BRANCHES, deserializedBranches);
        } else {
            writeContents(CURRENTBRANCH, b);
        }
    }

    /** clears and serializes both stages */
    private static void stageClear(HashMap<String, ArrayList<String>> stageAdd,
                                   HashMap<String, ArrayList<String>> stageRemove) {
        stageAdd.clear();
        stageRemove.clear();
        writeObject(STAGINGAA, stageAdd);
        writeObject(STAGINGAR, stageRemove);
    }

    /** finds and returns the full sha1 hash of a 6 digit sha1.
     * If given a full sha1 hash, will check
     *  if it exists and returns it. Returns null if does not exist. */
    private static String abbreviatedSha1(String commitID,
                                          TreeMap<String, String> deserializedMainT) {
        if (deserializedMainT.containsKey(commitID) || commitID.length() >= 6) {
            if (commitID.length() >= 6) {
                for (String key : deserializedMainT.keySet()) {
                    if (key.startsWith(commitID)) {
                        return key;
                    }
                }
                return null;
            }
            return commitID;
        }
        return null;
    }

    private static void printModified(Commit currentCommit,
                                      HashMap<String, ArrayList<String>> stageAdd,
                                      HashMap<String, ArrayList<String>> stageRemove) {
        for (String key : stageAdd.keySet()) {
            File f = join(CWD, key);
            if (!f.exists()) {
                System.out.println(key + " (deleted)");
            } else {
                String fileSha = sha1(readContents(f));
                if (!stageAdd.get(key).get(0).equals(fileSha)) {
                    System.out.println(key + " (modified)");
                }
            }
        }
        for (Map.Entry<String, ArrayList<String>> entry
                : currentCommit.committedFiles().entrySet()) {
            String name = entry.getKey();
            File f = join(CWD, name);
            if (!stageRemove.containsKey(name) && !f.exists()) {
                System.out.println(name + " (deleted)");
            } else if (f.exists()) {
                String fileSha = sha1(readContents(f));
                if (!entry.getValue().get(0).equals(fileSha) && !stageAdd.containsKey(name)) {
                    System.out.println(name + " (modified)");
                }
            }
        }
    }

    private static void printUntrackedFiles(Commit currentCommit,
                                            HashMap<String, ArrayList<String>> stageAdd,
                                            HashMap<String, ArrayList<String>> stageRemove) {
        List<String> filesInCWD = plainFilenamesIn(CWD);
        for (int i = 0; i < filesInCWD.size(); i += 1) {
            String name = filesInCWD.get(i);
            if (!stageAdd.containsKey(name)
                    && !currentCommit.committedFiles().containsKey(name)) {
                System.out.println(name);
            } else {
                File f = join(CWD, name);
                if (stageRemove.containsKey(name) && f.exists()) {
                    System.out.println(name);
                }
            }
        }
    }

    /** sees if there is an untracked file */
    private static boolean untrackedExists(Commit aPreviousCommit, Commit currentCommit,
                                           HashMap<String, ArrayList<String>> stageAdd,
                                           List<String> filesInCWD) {
        for (int i = 0; i < filesInCWD.size(); i += 1) {
            String name = filesInCWD.get(i);
            if (aPreviousCommit.committedFiles().containsKey(name)
                    && !stageAdd.containsKey(name)
                    && !currentCommit.committedFiles().containsKey(name)) {
                File f = join(CWD, name);
                String fileSha = sha1(readContents(f));
                if (!fileSha.equals(aPreviousCommit.committedFiles().get(name).get(0))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** gets turns commit to a file in COMMIT_FOLDER */
    private static File commitToFile(Commit c, String name) {
        File cf = join(COMMIT_FOLDER, name);
        writeObject(cf, c);
        return cf;
    }

    //DESERIALIZING METHODS

    private static TreeMap<String, String> deserializeMain() {
        @SuppressWarnings("unchecked")
        TreeMap<String, String> t = (TreeMap<String, String>)
                readObject(MAINTREE, TreeMap.class);
        return t;
    }

    private static HashMap<String, ArrayList<String>> deserializeStageAdd() {
        @SuppressWarnings("unchecked")
        HashMap<String, ArrayList<String>> s = (HashMap<String, ArrayList<String>>)
                readObject(STAGINGAA, HashMap.class);
        return s;
    }

    private static LinkedHashMap<String, String> deserializeBranch() {
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, String> b = (LinkedHashMap<String, String>)
                readObject(BRANCHES, LinkedHashMap.class);
        return b;
    }

    private static HashMap<String, ArrayList<String>> deserializeStageRemove() {
        @SuppressWarnings("unchecked")
        HashMap<String, ArrayList<String>> r = (HashMap<String, ArrayList<String>>)
                readObject(STAGINGAR, HashMap.class);
        return r;
    }

    private static HashMap<String, LinkedHashMap<String, Date>> deserializeAncestors() {
        @SuppressWarnings("unchecked")
        HashMap<String, LinkedHashMap<String, Date>> a =
                (HashMap<String, LinkedHashMap<String, Date>>)
                        readObject(COMMITANCESTORS, HashMap.class);
        return a;
    }
}

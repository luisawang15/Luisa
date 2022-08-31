package gitlet;


import java.io.File;

import static gitlet.Utils.*;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Luisa Wang
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            exitWithError("Please enter a command.");
        }
        String firstArg = args[0];
        switch(firstArg) {
            case "status":
                inGitletDir();
                validateNumArgs(args, 1);
                Repository.status();
                break;
            case "init":
                validateNumArgs(args, 1);
                Repository.init();
                break;
            case "add":
                inGitletDir();
                validateNumArgs(args, 2);
                Repository.add(args[1]);
                break;
            case "checkout":
                inGitletDir();
                if (args.length == 3) {
                    if (!args[1].equals("--")) {
                        exitWithError("Incorrect operands.");
                    }
                    Repository.checkout("current", args[2]);
                } else if (args.length == 4) {
                    if (!args[2].equals("--")) {
                        exitWithError("Incorrect operands.");
                    }
                    Repository.checkout(args[1], args[3]);
                } else if (args.length == 2) {
                    Repository.checkout(args[1]);
                } else {
                    exitWithError("Incorrect operands.");
                }
                break;
            case "log":
                inGitletDir();
                validateNumArgs(args, 1);
                Repository.log();
                break;
            case "commit":
                inGitletDir();
                if (args.length == 1 || args[1].isBlank()) {
                    exitWithError("Please enter a commit message.");
                }
                validateNumArgs(args, 2);
                Repository.makeCommit(args[1], null, null);
                break;
            case "rm":
                inGitletDir();
                validateNumArgs(args, 2);
                Repository.rm(args[1]);
                break;
            case "find":
                inGitletDir();
                validateNumArgs(args, 2);
                Repository.find(args[1]);
                break;
            case "global-log":
                inGitletDir();
                validateNumArgs(args, 1);
                Repository.globalLog();
                break;
            case "branch":
                inGitletDir();
                validateNumArgs(args, 2);
                Repository.branch(args[1]);
                break;
            case "rm-branch":
                inGitletDir();
                validateNumArgs(args, 2);
                Repository.rmBranch(args[1]);
                break;
            case "reset":
                inGitletDir();
                validateNumArgs(args, 2);
                Repository.reset(args[1]);
                break;
            case "merge":
                inGitletDir();
                validateNumArgs(args, 2);
                Repository.merge(args[1]);
                break;
            case "add-remote":
                validateNumArgs(args, 3);
                Repository.addremote(args[1], args[2]);
                break;
            case "rm-remote":
                validateNumArgs(args, 2);
                Repository.rmremote(args[1]);
                break;
            case "push":
                validateNumArgs(args, 3);
                Repository.push(args[1], args[2]);
                break;
            case "fetch":
                validateNumArgs(args, 3);
                Repository.fetch(args[1], args[2]);
                break;
            case "pull":
                validateNumArgs(args, 3);
                Repository.pull(args[1], args[2]);
                break;
            default:
                exitWithError("No command with that name exists.");
        }
    }

    //@source lab 6
    private static void validateNumArgs(String[] args, int n) {
        if (args.length != n) {
            exitWithError("Incorrect operands.");
        }
    }

    /** checks to see if program is used in an initialized Gitlet directory */
    private static void inGitletDir() {
        if (!Repository.GITLET_DIR.exists()) {
            exitWithError("Not in an initialized Gitlet directory.");
        }
    }
}

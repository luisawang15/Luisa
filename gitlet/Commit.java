package gitlet;

import java.io.Serializable;
import java.util.*;


/** Represents a gitlet commit object.
 *  Stores the time, message, and file versions at the time of the commit
 *  Makes it possible to find old file versions.
 *
 *  @author Luisa Wang
 */
public class Commit implements Serializable {

    /** The message of this Commit. */
    private final String message;

    /** date/time commit was made */
    private final Date date;

    /** the parent commit, Sha1-Hash. "init" if it is the first commit */
    private final String parent;

    /** for merges */
    private String secondParent = null;

    /** hashmap of filename and Array list of blob sha1 and
     * and the file path of the data stored at current commit */
    private HashMap<String, ArrayList<String>> blobs = new HashMap<>();

    /** creates inital commit */
    public Commit() {
        message = "initial commit";
        date = new Date(0);
        parent = "init";
    }

    /** creates commits that aren't the initial */
    public Commit(String m, HashMap<String, ArrayList<String>> stageAdd,
                  HashMap<String, ArrayList<String>> stageRemove,
                  String parentSha1, Commit parentInfo) {
        message = m;
        date = new Date();
        parent = parentSha1;
        for (Map.Entry<String, ArrayList<String>> entry : parentInfo.committedFiles().entrySet()) {
            if (!stageRemove.containsKey(entry.getKey())) {
                blobs.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, ArrayList<String>> entry : stageAdd.entrySet()) {
            blobs.put(entry.getKey(), entry.getValue());
        }
    }

    /** returns the sha1 hash of the second parent. null if there is not one */
    public String getSecondParent() {
        return secondParent;
    }

    /** sets the second parent's sha1 hash. For merges */
    public void setSecondParent(String sha) {
        secondParent = sha;
    }

    /** return parent's sha1 hash. If it is the first commit, will return init */
    public String parent() {
        return parent;
    }

    /** prints the date */
    public void printDate() {
        String d = String.format("%1$ta %1$tb %1$td %1$tT %1$tY %1$tz", date);
        System.out.println("Date: " + d);
    }

    /** returns the message */
    public String getMessage() {
        return message;
    }

    /** prints commit message */
    public void printMessage() {
        System.out.println(message);
    }

    /** returns a hashMap of the committed blobs */
    public HashMap<String, ArrayList<String>> committedFiles() {
        return blobs;
    }

    public Date getDate() {
        return date;
    }
}

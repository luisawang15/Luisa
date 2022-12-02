package client

// CS 161 Project 2

// You MUST NOT change these default imports. ANY additional imports
// may break the autograder!

import (
	"encoding/json"

	userlib "github.com/cs161-staff/project2-userlib"
	"github.com/google/uuid"

	// hex.EncodeToString(...) is useful for converting []byte to string

	// Useful for string manipulation
	"strings"

	// Useful for formatting strings (e.g. `fmt.Sprintf`).
	"fmt"

	// Useful for creating new error messages to return using errors.New("...")
	"errors"

	// Optional.
	_ "strconv"
)

// This serves two purposes: it shows you a few useful primitives,
// and suppresses warnings for imports not being used. It can be
// safely deleted!
func someUsefulThings() {

	// Creates a random UUID.
	randomUUID := uuid.New()

	// Prints the UUID as a string. %v prints the value in a default format.
	// See https://pkg.go.dev/fmt#hdr-Printing for all Golang format string flags.
	userlib.DebugMsg("Random UUID: %v", randomUUID.String())

	// Creates a UUID deterministically, from a sequence of bytes.
	hash := userlib.Hash([]byte("user-structs/alice"))
	deterministicUUID, err := uuid.FromBytes(hash[:16])
	if err != nil {
		// Normally, we would `return err` here. But, since this function doesn't return anything,
		// we can just panic to terminate execution. ALWAYS, ALWAYS, ALWAYS check for errors! Your
		// code should have hundreds of "if err != nil { return err }" statements by the end of this
		// project. You probably want to avoid using panic statements in your own code.
		panic(errors.New("An error occurred while generating a UUID: " + err.Error()))
	}
	userlib.DebugMsg("Deterministic UUID: %v", deterministicUUID.String())

	// Declares a Course struct type, creates an instance of it, and marshals it into JSON.
	type Course struct {
		name      string
		professor []byte
	}

	course := Course{"CS 161", []byte("Nicholas Weaver")}
	courseBytes, err := json.Marshal(course)
	if err != nil {
		panic(err)
	}

	userlib.DebugMsg("Struct: %v", course)
	userlib.DebugMsg("JSON Data: %v", courseBytes)

	// Generate a random private/public keypair.
	// The "_" indicates that we don't check for the error case here.
	var pk userlib.PKEEncKey
	var sk userlib.PKEDecKey
	pk, sk, _ = userlib.PKEKeyGen()
	userlib.DebugMsg("PKE Key Pair: (%v, %v)", pk, sk)

	// Here's an example of how to use HBKDF to generate a new key from an input key.
	// Tip: generate a new key everywhere you possibly can! It's easier to generate new keys on the fly
	// instead of trying to think about all of the ways a key reuse attack could be performed. It's also easier to
	// store one key and derive multiple keys from that one key, rather than
	originalKey := userlib.RandomBytes(16)
	derivedKey, err := userlib.HashKDF(originalKey, []byte("mac-key"))
	if err != nil {
		panic(err)
	}
	userlib.DebugMsg("Original Key: %v", originalKey)
	userlib.DebugMsg("Derived Key: %v", derivedKey)

	// A couple of tips on converting between string and []byte:
	// To convert from string to []byte, use []byte("some-string-here")
	// To convert from []byte to string for debugging, use fmt.Sprintf("hello world: %s", some_byte_arr).
	// To convert from []byte to string for use in a hashmap, use hex.EncodeToString(some_byte_arr).
	// When frequently converting between []byte and string, just marshal and unmarshal the data.
	//
	// Read more: https://go.dev/blog/strings

	// Here's an example of string interpolation!
	_ = fmt.Sprintf("%s_%d", "file", 1)
}

const Maxbytes int = 1000

type Info struct {
	Data []byte
	Tag []byte
}

type File struct {
	OwnerShared map[string][]string
	FileShared map[string]uuid.UUID
	Key []byte
	HMACkey []byte
	NumberOfSections int
	FileSections map[int]uuid.UUID
}

type FileContent struct {
	Content []byte
	NumberOfBytes int
}

type Mailbox struct {
	TagKey []byte
	DecryptKey []byte
	UUID uuid.UUID
	RSAKey userlib.PKEEncKey
	OwnerSig userlib.PKEEncKey
	Shared bool
	Sharer string
	Message string
}

type Invitation struct {
	UUID uuid.UUID
	Key []byte
}

// This is the type definition for the User struct.
// A Go struct is like a Python or Java class - it can have attributes
// (e.g. like the Username attribute) and methods (e.g. like the StoreFile method below).
type User struct {
	Username string
	Password string
	HashUsername []byte
	RootKey []byte
	RsaPrivate userlib.PKEDecKey
	SignaturePrivate userlib.DSSignKey

	// You can add other attributes here if you want! But note that in order for attributes to
	// be included when this struct is serialized to/from JSON, they must be capitalized.
	// On the flipside, if you have an attribute that you want to be able to access from
	// this struct's methods, but you DON'T want that value to be included in the serialized value
	// of this struct that's stored in datastore, then you can use a "private" variable (e.g. one that
	// begins with a lowercase letter).
}

// NOTE: The following methods have toy (insecure!) implementations.
func InitUser(username string, password string) (userdataptr *User, err error) {
	var userdata User
	if len(username) == 0 {
		return &userdata, errors.New("username length can not be zero")
	}
	_, ok := userlib.KeystoreGet(username)
	if ok {
		return nil, errors.New("username already exists!")
	}
	userdata.Username = username
	userdata.Password = password
	userdata.HashUsername = userlib.Hash([]byte(username))
	pk, sk, _ := userlib.PKEKeyGen()
	userdata.RsaPrivate = sk
	userlib.KeystoreSet(username + " and public key", pk)
	sk, pk, _ = userlib.DSKeyGen()
	userdata.SignaturePrivate = sk
	userlib.KeystoreSet(username, pk)
	salt := userlib.RandomBytes(16)
	id, err := uuid.FromBytes(userlib.Hash(append(userdata.HashUsername, []byte("salt")...))[:16])
	if err != nil {
		return nil, err
	}
	userlib.DatastoreSet(id, salt)
	PBKDF := userlib.Argon2Key([]byte(password), salt, 48)
	userdata.RootKey = PBKDF[32:]
	userBytes, err := json.Marshal(userdata)
	if err != nil {
		return nil, err
	}
	id, err = uuid.FromBytes(userdata.HashUsername[:16])
	if err != nil {
		return nil, err
	}
	err = StoreInDatastore(userBytes, PBKDF[:16], PBKDF[16:32], id)
	if err != nil {
		return nil, err
	}
	return &userdata, nil
}

func GetUser(username string, password string) (userdataptr *User, err error) {
	var userdata User
	id, err := uuid.FromBytes(userlib.Hash(append(userlib.Hash([]byte(username)), []byte("salt")...))[:16])
	if err != nil {
		return nil, err
	}
	salt, ok := userlib.DatastoreGet(id)
	if !ok {
		return nil, errors.New("user does not exist!")
	}
	PBKDF := userlib.Argon2Key([]byte(password), salt, 48)
	id, err = uuid.FromBytes(userlib.Hash([]byte(username))[:16])
	if err != nil {
		return nil, err
	}
	info, ok := userlib.DatastoreGet(id)
	if !ok {
		return nil, errors.New("datastore fail to get user")
	}
	var storage Info
	err = json.Unmarshal(info, &storage)
	if err != nil {
		return nil, err
	}
	hmac, _ := userlib.HMACEval(PBKDF[16:32], storage.Data)
	ok = userlib.HMACEqual(hmac, storage.Tag)
	if !ok {
		return nil, errors.New("HMAC doesn't match")
	}
	userBytes := userlib.SymDec(PBKDF[:16], storage.Data)
	err = json.Unmarshal(userBytes, &userdata)
	if err != nil {
		return nil, err
	}
	userdataptr = &userdata
	return userdataptr, nil
}

func (userdata *User) StoreFile(filename string, content []byte) (err error) {
	_, err = GetUser(userdata.Username, userdata.Password)
	if err != nil {
		return err
	}
	storageKey, err := FileID(userdata.HashUsername, filename)
	if err != nil {
		return err
	}
	contentBytes := content
	value, ok := userlib.DatastoreGet(storageKey)
	var box Mailbox
	var file File
	if ok {
		//file already exists
		file, box, err = userdata.decryptFile(storageKey, filename, value)
		if err != nil {
			return err
		}
	} else {
		//file doesn't exist
		box.Message = userdata.Username + filename + "struct"
		keys, err := userlib.HashKDF(userdata.RootKey, []byte(box.Message))
		if err != nil {
			return err
		}
		box.DecryptKey = keys[:16]
		box.TagKey = keys[16:]
		box.UUID, err = uuid.FromBytes(userlib.Hash([]byte(box.Message))[:16])
		if err != nil {
			return err
		}
		box.Shared = false
		boxBytes , err := json.Marshal(box)
		if err != nil {
			return err
		}
		boxKeys, err := userlib.HashKDF(userdata.RootKey, []byte(userdata.Username + filename))
		if err != nil {
			return err
		}
		err = StoreInDatastore(boxBytes, boxKeys[:16], boxKeys[16:], storageKey)
		if err != nil {
			return err
		}
		keys, err = userlib.HashKDF(userdata.RootKey, []byte(box.Message + "sections"))
		if err != nil {
			return err
		}
		file.Key = keys[:16]
		file.HMACkey = keys[16:]
		file.OwnerShared = make(map[string][]string)
		file.FileShared = make(map[string]uuid.UUID)
	}
	file.NumberOfSections = 0
	file.FileSections = make(map[int]uuid.UUID)

	id := randomUUID()
	contentStruct := new(FileContent)
	contentStruct.NumberOfBytes = 0
	remaining, amount, _ := contentStruct.Fill(contentBytes)
	fileContentBytes, err := json.Marshal(contentStruct)
	if err != nil {
		return err
	}
	err = StoreInDatastore(fileContentBytes, file.Key, file.HMACkey, id)
	if err != nil {
		return err
	}
	file.FileSections[file.NumberOfSections] = id
	for amount > 0 {
		file.NumberOfSections += 1
		id = randomUUID()
		contentStruct = new(FileContent)
		contentStruct.NumberOfBytes = 0
		remaining, amount, _ = contentStruct.Fill(remaining)
		fileContentBytes, err = json.Marshal(contentStruct)
		if err != nil {
			return err
		}
		err = StoreInDatastore(fileContentBytes, file.Key, file.HMACkey, id)
		if err != nil {
			return err
		}
		file.FileSections[file.NumberOfSections] = id
	}
	fileContentBytes, err = json.Marshal(file)
	if err != nil {
		return err
	}
	err = StoreInDatastore(fileContentBytes, box.DecryptKey, box.TagKey, box.UUID)
	if err != nil {
		return err
	}
	return nil
}

func (userdata *User) AppendToFile(filename string, content []byte) error {
	_, err := GetUser(userdata.Username, userdata.Password)
	if err != nil {
		return err
	}
	id, err := FileID(userdata.HashUsername, filename)
	if err != nil {
		return err
	}
	data, ok := userlib.DatastoreGet(id)
	if !ok {
		return errors.New(strings.ToTitle("file not found"))
	}
	file, box, err := userdata.decryptFile(id, filename, data)
	if err != nil {
		return err
	}
	contentBytes := content
	id = file.FileSections[file.NumberOfSections]
	cStruct, err := DecryptFileContent(file, id)
	if err != nil {
		return err
	}
	remaining, amount, ok := cStruct.Fill(contentBytes)
	if ok { // must store if altered
		b, err := json.Marshal(cStruct)
		if err != nil {
			return err
		}
		err = StoreInDatastore(b, file.Key, file.HMACkey, id)
		if err != nil {
			return err
		}
	}
	for amount > 0 {
		file.NumberOfSections += 1
		id = randomUUID()
		contentStruct := new(FileContent)
		contentStruct.NumberOfBytes = 0
		remaining, amount, _ = contentStruct.Fill(remaining)
		fileContentBytes, err := json.Marshal(contentStruct)
		if err != nil {
			return err
		}
		err = StoreInDatastore(fileContentBytes, file.Key, file.HMACkey, id)
		if err != nil {
			return err
		}
		file.FileSections[file.NumberOfSections] = id
	}
	fileBytes, err := json.Marshal(file)
	if err != nil {
		return err
	}
	err = StoreInDatastore(fileBytes, box.DecryptKey, box.TagKey, box.UUID)
	if err != nil {
		return err
	}
	return nil
}

func (userdata *User) LoadFile(filename string) (content []byte, err error) {
	_, err = GetUser(userdata.Username, userdata.Password)
	if err != nil {
		return nil, err
	}
	storageKey, err := FileID(userdata.HashUsername, filename)
	if err != nil {
		return nil, err
	}
	data, ok := userlib.DatastoreGet(storageKey)
	if !ok {
		return nil, errors.New(strings.ToTitle("file not found!"))
	}
	var file File
	file, _, err = userdata.decryptFile(storageKey, filename, data)
	if err != nil {
		return nil, err
	}
	var count int = 0
	var contentStruct FileContent
	var bytes []byte
	for count <= file.NumberOfSections {
		id := file.FileSections[count]
		contentStruct, err = DecryptFileContent(file, id)
		if err != nil {
			return nil, err
		}
		bytes = append(bytes, contentStruct.Content...)
		count += 1
	}
	content = bytes
	return content, err
}

func (userdata *User) CreateInvitation(filename string, recipientUsername string) (
	invitationPtr uuid.UUID, err error) {
		recipientbox := new(Mailbox)
		var ok bool
		id := randomUUID()
		_, err = GetUser(userdata.Username, userdata.Password)
		if err != nil {
			return id, err
		}
	temp, err := uuid.FromBytes(userlib.Hash([]byte(recipientUsername))[:16])
	if err != nil {
                return id, err 
	}
	_, ok = userlib.DatastoreGet(temp)
		if !ok {
			return id, errors.New("recipient doesn't exist")
		}
		fileid, err := FileID(userdata.HashUsername, filename)
		if err != nil {
			return id, err
		}
		data, ok := userlib.DatastoreGet(fileid)
		if !ok {
			return id, errors.New(strings.ToTitle("mailbox not found"))
		}
		file, box, err := userdata.decryptFile(fileid, filename, data)
		if err != nil {
			return id, err
		}
		if box.Shared {
			recipientbox.Sharer = box.Sharer
			file.OwnerShared[box.Sharer] = append(file.OwnerShared[box.Sharer], recipientUsername)
			recipientbox.OwnerSig = box.OwnerSig
		} else {
			var lst []string
			file.OwnerShared[recipientUsername] = lst
			recipientbox.Sharer = recipientUsername
			recipientbox.OwnerSig, ok = userlib.KeystoreGet(userdata.Username)
			if !ok {
				return id, errors.New(strings.ToTitle("owner signature not found"))
			}
		}
		file.FileShared[recipientUsername] = id
		fileBytes, err := json.Marshal(file)
		if err != nil {
			return id, err
		}
		err = StoreInDatastore(fileBytes, box.DecryptKey, box.TagKey, box.UUID)
		if err != nil {
			return id, err
		}
		recipientbox.TagKey = box.TagKey
		recipientbox.DecryptKey = box.DecryptKey
		recipientbox.UUID = box.UUID
		recipientbox.Shared = true
		err = userdata.StoreInvitation(recipientbox, id, recipientUsername)
		if err != nil {
			return id, err
		}
	return id, nil
}

func (userdata *User) AcceptInvitation(senderUsername string, invitationPtr uuid.UUID, filename string) error {
	var box Mailbox
	var ok bool
	_, err := GetUser(userdata.Username, userdata.Password)
	if err != nil {
		return err
	}
	id, err := FileID(userdata.HashUsername, filename)
	if err != nil {
		return err
	}
	_, ok = userlib.DatastoreGet(id)
	if ok {
		return errors.New("filename already exists for this user")
	}
	box.UUID = invitationPtr
	box.Shared = true
	box.RSAKey, ok = userlib.KeystoreGet(senderUsername)
	if !ok {
		return errors.New(strings.ToTitle("sender's public signature not found"))
	}
	invitebox, changed, err := userdata.getInvitation(box)
	if err != nil {
		return err
	}
	if changed {
		box.RSAKey = invitebox.OwnerSig
	}
	box.OwnerSig = invitebox.OwnerSig
	boxBytes, err := json.Marshal(box)
	if err != nil {
		return err
	}
	keys, err := userlib.HashKDF(userdata.RootKey, []byte(userdata.Username + filename))
	if err != nil {
		return err
	}
	err = StoreInDatastore(boxBytes, keys[:16], keys[16:], id)
	if err != nil {
		return err
	}
	return nil
}

func (userdata *User) RevokeAccess(filename string, recipientUsername string) error {
	_, err := GetUser(userdata.Username, userdata.Password)
	if err != nil {
		return err
	}
	id, err := FileID(userdata.HashUsername, filename)
	if err != nil {
		return err
	}
	data, ok := userlib.DatastoreGet(id)
	if !ok {
		return errors.New(strings.ToTitle("couldn't find file"))
	}
	file, box, err := userdata.decryptFile(id, filename, data)
	if _, ok = file.FileShared[recipientUsername]; !ok {
		return errors.New("never shared with recipient")
	}
	userlib.DatastoreDelete(box.UUID)
	box.Message = box.Message + "revoked" + recipientUsername //recalcuate
	keys, err := userlib.HashKDF(userdata.RootKey, []byte(box.Message))
	if err != nil {
		return err
	}
	box.DecryptKey = keys[:16]
	box.TagKey = keys[16:]
	box.UUID, err = uuid.FromBytes(userlib.Hash([]byte(box.Message))[:16])
	if err != nil {
		return err
	}
	for _, item := range file.OwnerShared[recipientUsername] {
		userlib.DatastoreDelete(file.FileShared[item])
		delete(file.FileShared, item)
	}
	userlib.DatastoreDelete(file.FileShared[recipientUsername])
	delete(file.FileShared, recipientUsername)
	delete(file.OwnerShared, recipientUsername)
	sharedbox := new(Mailbox)
	for key, element := range file.OwnerShared {
		sharedbox.TagKey = box.TagKey
		sharedbox.DecryptKey = box.DecryptKey
		sharedbox.UUID = box.UUID
		sharedbox.OwnerSig, ok = userlib.KeystoreGet(userdata.Username)
		if !ok {
			return errors.New(strings.ToTitle("owner signature not found"))
		}
		sharedbox.Shared = true
		sharedbox.Sharer = key
		err = userdata.StoreInvitation(sharedbox, file.FileShared[key], key)
		if err != nil {
			return err
		}
		for _, item := range element {
			sharedbox = new(Mailbox)
			sharedbox.TagKey = box.TagKey
			sharedbox.DecryptKey = box.DecryptKey
			sharedbox.UUID = box.UUID
			sharedbox.Shared = true
			sharedbox.Sharer = key
			sharedbox.OwnerSig, ok = userlib.KeystoreGet(userdata.Username)
			if !ok {
				return errors.New(strings.ToTitle("owner signature not found"))
			}
			err = userdata.StoreInvitation(sharedbox, file.FileShared[item], item)
			if err != nil {
				return err
			}
		}
	}
	var newFile File
	newFile.OwnerShared = file.OwnerShared
	newFile.FileShared = file.FileShared
	newFile.NumberOfSections = file.NumberOfSections
	keys, err = userlib.HashKDF(userdata.RootKey, []byte(box.Message + "sections"))
	if err != nil {
		return err
	}
	newFile.Key = keys[:16]
	newFile.HMACkey = keys[16:]
	var count int = 0
	newFile.FileSections = make(map[int]uuid.UUID)

	for count <= newFile.NumberOfSections {
		newContent := new(FileContent)
		contentStruct, err := DecryptFileContent(file, file.FileSections[count])
		newContent.NumberOfBytes = contentStruct.NumberOfBytes
		newContent.Content = contentStruct.Content
		fileContentBytes, err := json.Marshal(newContent)
		if err != nil {
			return err
		}
		var randid uuid.UUID = randomUUID()
		err = StoreInDatastore(fileContentBytes, newFile.Key, newFile.HMACkey, randid)
		if err != nil {
			return err
		}
		newFile.FileSections[count] = randid
		count += 1
	}
	fileContentBytes, err := json.Marshal(newFile) //renencrypt new file
	if err != nil {
		return err
	}
	err = StoreInDatastore(fileContentBytes, box.DecryptKey, box.TagKey, box.UUID)
	if err != nil {
		return err
	}
	boxBytes , err := json.Marshal(box)
	if err != nil {
		return err
	}
	boxKeys, err := userlib.HashKDF(userdata.RootKey, []byte(userdata.Username + filename))
	if err != nil {
		return err
	}
	err = StoreInDatastore(boxBytes, boxKeys[:16], boxKeys[16:], id)
	if err != nil {
		return err
	}
	return nil
}




func FileID(hashusername []byte, filename string) (id uuid.UUID, err error) {
	return uuid.FromBytes(userlib.Hash(append(hashusername, userlib.Hash([]byte(filename))...))[:16])
}

func randomUUID() uuid.UUID {
	id := uuid.New()
	_, ok := userlib.DatastoreGet(id)
	for ok {
		id = uuid.New()
		_, ok = userlib.DatastoreGet(id)
	}
	return id
}

func (fileContent *FileContent) Fill(content []byte) ([]byte, int, bool) {
	if fileContent.NumberOfBytes == Maxbytes {
		return content, len(content), false
	}
	var empty int = Maxbytes - fileContent.NumberOfBytes
	if len(content) < empty {
		empty = len(content)
	}
	fileContent.Content = append(fileContent.Content, content[:empty]...)
	num := len(content) - empty
	return content[empty:], num, true
}

func StoreInDatastore(content []byte, enckey []byte, tagkey []byte, id uuid.UUID) error {
	var storage Info
	storage.Data = userlib.SymEnc(enckey, userlib.RandomBytes(16), content)
	storage.Tag, _ = userlib.HMACEval(tagkey, storage.Data)
	storageBytes, err := json.Marshal(storage)
	if err != nil {
		return err
	}
	userlib.DatastoreSet(id, storageBytes)
	return nil
}

func (userdata *User) StoreInvitation(recipientbox *Mailbox, id uuid.UUID, recipientUsername string) error {
	var invite Invitation
	var storage Info
	invite.UUID = randomUUID()
	invite.Key = userlib.RandomBytes(16)
	mailBytes, err := json.Marshal(invite)
	if err != nil {
		return err
	}
	pubKey, ok := userlib.KeystoreGet(recipientUsername + " and public key")
	if !ok {
		return errors.New(strings.ToTitle("recipient public key not found"))
	}
	storage.Data, err = userlib.PKEEnc(pubKey, mailBytes)
	if err != nil {
		return err
	}
	storage.Tag, err = userlib.DSSign(userdata.SignaturePrivate, storage.Data)
	if err != nil {
		return err
	}
	mailBytes, err = json.Marshal(storage)
	if err != nil {
		return err
	}
	userlib.DatastoreSet(id, mailBytes)
	mailBytes, err = json.Marshal(recipientbox)
	if err != nil {
		return err
	}
	keys, err := userlib.HashKDF(invite.Key, []byte("decrypt"))
	if err != nil {
		return err
	}
	err = StoreInDatastore(mailBytes, keys[:16], keys[16:], invite.UUID)
	if err != nil {
		return err
	}
	return nil
}

func (userdata *User) decryptFile(id uuid.UUID, filename string, value []byte) (File, Mailbox, error) {
	var file File
	var fileStorage Info
	box, err := userdata.getBox(id, filename, value)
	if err != nil {
		return file, box, err
	}
	value, ok := userlib.DatastoreGet(box.UUID)
	if !ok {
		return file, box, errors.New("does not exist in datastore")
	}
	err = json.Unmarshal(value, &fileStorage)
	if err != nil {
		return file, box, err
	}
	hmac, _ := userlib.HMACEval(box.TagKey, fileStorage.Data)
	ok = userlib.HMACEqual(hmac, fileStorage.Tag)
	if !ok {
		return file, box, errors.New("HMAC doesn't match")
	}
	fileBytes := userlib.SymDec(box.DecryptKey, fileStorage.Data)
	err = json.Unmarshal(fileBytes, &file)
	if err != nil {
		return file, box, err
	}
	return file, box, nil
}

func (userdata *User) getBox(id uuid.UUID, filename string, input []byte) (Mailbox, error) {
	var storage Info
	var box Mailbox
	err := json.Unmarshal(input, &storage)
	if err != nil {
		return box, err
	}
	keys, err := userlib.HashKDF(userdata.RootKey, []byte(userdata.Username + filename))
	if err != nil {
		return box, err
	}
	hmac, _ := userlib.HMACEval(keys[16:], storage.Data)
	ok := userlib.HMACEqual(hmac, storage.Tag)
	if !ok {
		return box, errors.New("HMAC doesn't match")
	}
	boxBytes := userlib.SymDec(keys[:16], storage.Data)
	err = json.Unmarshal(boxBytes, &box)
	if err != nil {
		return box, err
	}
	if box.Shared {
		secondbox, changed, err := userdata.getInvitation(box)
		if err != nil {
			return secondbox, err
		}
		if changed {
			box.RSAKey = box.OwnerSig
			bytes, err := json.Marshal(box)
			if err != nil {
				return secondbox, err
			}
			err = StoreInDatastore(bytes, keys[:16], keys[16:], id)
			if err != nil {
				return secondbox, err
			}
		}
		return secondbox, err
	}
	return box, nil
}

func (userdata *User) getInvitation(box Mailbox) (Mailbox, bool, error) {
	var storage Info
	var secondbox Mailbox
	var changed bool = false
	var invite Invitation
	value, ok := userlib.DatastoreGet(box.UUID)
	if !ok {
		return box, changed, errors.New("does not exist in datastore")
	}
	err := json.Unmarshal(value, &storage)
	if err != nil {
		return box, changed, err
	}
	err = userlib.DSVerify(box.RSAKey, storage.Data, storage.Tag)
	if err != nil {
		err = userlib.DSVerify(box.OwnerSig, storage.Data, storage.Tag)
		if err != nil {
			return secondbox, changed, err
		}
		changed = true
	}
	boxBytes, err := userlib.PKEDec(userdata.RsaPrivate, storage.Data)
	if err != nil {
		return secondbox, changed, err
	}
	err = json.Unmarshal(boxBytes, &invite)
	if err != nil {
		return secondbox, changed, err
	}
	keys, err := userlib.HashKDF(invite.Key , []byte("decrypt"))
	if err != nil {
		return secondbox, changed, err
	}
	value, ok = userlib.DatastoreGet(invite.UUID)
	if !ok {
		return secondbox, changed, errors.New("can't find second mailbox in datastore")
	}
	err = json.Unmarshal(value, &storage)
	if err != nil {
		return secondbox, changed, err
	}
	hmac, _ := userlib.HMACEval(keys[16:], storage.Data)
	ok = userlib.HMACEqual(hmac, storage.Tag)
	if !ok {
		return secondbox, changed, errors.New("HMAC doesn't match")
	}
	boxBytes = userlib.SymDec(keys[:16], storage.Data)
	err = json.Unmarshal(boxBytes, &secondbox)
	if err != nil {
		return secondbox, changed, err
	}
	return secondbox, changed, nil
}

func DecryptFileContent(file File, id uuid.UUID) (FileContent, error) {
	var content FileContent
	var storage Info
	var bytes []byte
	bytes, ok := userlib.DatastoreGet(id)
	if !ok {
		return content, errors.New(strings.ToTitle("file not found."))
	}
	err := json.Unmarshal(bytes, &storage)
	if err != nil {
		return content, err
	}
	hmac, _ := userlib.HMACEval(file.HMACkey, storage.Data)
	ok = userlib.HMACEqual(hmac, storage.Tag)
	if !ok {
		return content, errors.New("HMAC doesn't match")
	}
	contentBytes := userlib.SymDec(file.Key, storage.Data)
	err = json.Unmarshal(contentBytes, &content)
	if err != nil {
		return content, err
	}
	return content, nil
}

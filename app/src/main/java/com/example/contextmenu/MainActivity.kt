package com.example.contextmenu

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.contextmenu.ui.theme.ContextMenuTheme

data class Contact(
    val id: Long,
    val name: String,
    val number: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ContextMenuTheme {
                var contacts by remember { mutableStateOf(listOf<Contact>()) }
                var showDialog by remember { mutableStateOf(false) }
                val context = LocalContext.current

                val requestPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    if (permissions[Manifest.permission.READ_CONTACTS] == true &&
                        permissions[Manifest.permission.WRITE_CONTACTS] == true) {
                        contacts = getContacts(context)
                    }
                }

                LaunchedEffect(Unit) {
                    if (hasContactPermission(context)) {
                        contacts = getContacts(context)
                    } else {
                        requestPermissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                        )
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ContactListScreen(
                        modifier = Modifier.padding(innerPadding),
                        contacts = contacts,
                        onContactsChanged = { contacts = it },
                        showDialog = showDialog,
                        onShowDialogChanged = { showDialog = it }
                    )
                }
            }
        }
    }
}

@Composable
fun ContactListScreen(
    modifier: Modifier = Modifier,
    contacts: List<Contact>,
    onContactsChanged: (List<Contact>) -> Unit,
    showDialog: Boolean,
    onShowDialogChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Column {
        AppBar(
            modifier = Modifier,
            onAscendingClicked = { onContactsChanged(contacts.sortedBy { it.name }) },
            onDescendingClicked = { onContactsChanged(contacts.sortedByDescending { it.name }) },
            onCreateClicked = { onShowDialogChanged(true) },
        )
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(contacts) { contact ->
                ContactItem(
                    contact = contact,
                    onDelete = { contactToDelete ->
                        deleteContact(context, contactToDelete.id)
                        onContactsChanged(getContacts(context)) // Refresh the contact list
                    },
                    onUpdate = { contactToUpdate ->
                        updateContact(context, contactToUpdate.id, contactToUpdate.name, contactToUpdate.number)
                        onContactsChanged(getContacts(context)) // Refresh the contact list
                    }
                )
            }
        }
    }

    if (showDialog) {
        AddContactDialog(
            onDismiss = { onShowDialogChanged(false) },
            onAddContact = { name, phone ->
                createContact(context, name, phone)
                onContactsChanged(getContacts(context)) // Refresh the contact list
            }
        )
    }
}

fun getContacts(context: Context): List<Contact> {
    val contacts = mutableListOf<Contact>()
    val cursor: Cursor? = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        null, null, null, null
    )

    cursor?.use {
        val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

        while (it.moveToNext()) {
            val id = it.getLong(idIndex)
            val name = it.getString(nameIndex)
            val number = it.getString(numberIndex)
            contacts.add(Contact(id, name, number))
        }
    }

    return contacts
}

fun hasContactPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(
    modifier: Modifier = Modifier,
    onAscendingClicked: () -> Unit,
    onDescendingClicked: () -> Unit,
    onCreateClicked: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("Contact List") },
        colors = TopAppBarDefaults.mediumTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = modifier,
        actions = {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Ascending") },
                    onClick = {
                        expanded = false
                        onAscendingClicked()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Descending") },
                    onClick = {
                        expanded = false
                        onDescendingClicked()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Create Contact") },
                    onClick = {
                        expanded = false
                        onCreateClicked()
                    }
                )
            }
        }
    )
}

fun createContact(context: Context, name: String, phone: String) {
    val contentResolver = context.contentResolver

    val contentValues = ContentValues().apply {
        put(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
        put(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
    }

    val rawContactUri = contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, contentValues)
    val rawContactId = ContentUris.parseId(rawContactUri!!)

    val dataValues = ContentValues().apply {
        put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
        put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
    }
    contentResolver.insert(ContactsContract.Data.CONTENT_URI, dataValues)

    val phoneValues = ContentValues().apply {
        put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
        put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
        put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
        put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
    }
    contentResolver.insert(ContactsContract.Data.CONTENT_URI, phoneValues)
}

fun updateContact(context: Context, contactId: Long, newName: String, newPhone: String) {
    val contentResolver = context.contentResolver

    val nameValues = ContentValues().apply {
        put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
    }
    contentResolver.update(
        ContactsContract.Data.CONTENT_URI,
        nameValues,
        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
        arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
    )

    val phoneValues = ContentValues().apply {
        put(ContactsContract.CommonDataKinds.Phone.NUMBER, newPhone)
    }
    contentResolver.update(
        ContactsContract.Data.CONTENT_URI,
        phoneValues,
        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
        arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
    )
}

fun deleteContact(context: Context, contactId: Long) {
    val contentResolver = context.contentResolver
    val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
    contentResolver.delete(uri, null, null)
}

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onAddContact: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Add New Contact") },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") }
                )
                TextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddContact(name, phone)
                    onDismiss()
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ContactItem(contact: Contact, onDelete: (Contact) -> Unit, onUpdate: (Contact) -> Unit) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var touchPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "${contact.name}: ${contact.number}",
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { offset ->
                            touchPosition = offset
                            showContextMenu = true
                        }
                    )
                }
        )

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = with(density) { DpOffset(touchPosition.x.toDp(), touchPosition.y.toDp()) }
        ) {
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    onDelete(contact)
                    showContextMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Update") },
                onClick = {
                    showUpdateDialog = true
                    showContextMenu = false
                }
            )
        }
    }

    if (showUpdateDialog) {
        UpdateContactDialog(
            contact = contact,
            onDismiss = { showUpdateDialog = false },
            onUpdateContact = { name, phone ->
                onUpdate(contact.copy(name = name, number = phone))
            }
        )
    }
}

@Composable
fun UpdateContactDialog(
    contact: Contact,
    onDismiss: () -> Unit,
    onUpdateContact: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(contact.name) }
    var phone by remember { mutableStateOf(contact.number) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Update Contact") },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") }
                )
                TextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onUpdateContact(name, phone)
                    onDismiss()
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
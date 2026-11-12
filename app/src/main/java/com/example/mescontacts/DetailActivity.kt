package com.example.mescontacts

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.widget.doAfterTextChanged
import com.example.mescontacts.databinding.ActivityDetailBinding
import java.io.File

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var original: Contact
    private var position = MainActivity.NEW_CONTACT

    /** Photo actuellement affichée (non enregistrée tant qu'on n'a pas appuyé sur « Enregistrer »). */
    private var currentPhotoPath: String? = null

    /** Fichier temporaire donné à l'application caméra. */
    private var cameraTempFile: File? = null

    /** Photos créées pendant cette session d'édition : supprimées si on annule. */
    private val newPhotos = ArrayList<String>()

    // --- Galerie -----------------------------------------------------------------------------
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) {
                // Sélection annulée : l'ancienne photo est conservée
                toast(R.string.msg_photo_selection_cancelled)
            } else {
                applyNewPhoto(uri)
            }
        }

    // --- Caméra ------------------------------------------------------------------------------
    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val temp = cameraTempFile
            cameraTempFile = null
            if (success && temp != null && temp.exists() && temp.length() > 0) {
                applyNewPhoto(Uri.fromFile(temp))
            } else {
                toast(R.string.msg_photo_capture_cancelled)
            }
            temp?.delete()
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera() else toast(R.string.msg_camera_permission_denied)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val contact = IntentCompat.getParcelableExtra(intent, EXTRA_CONTACT, Contact::class.java)
        if (contact == null) {
            toast(R.string.msg_invalid_contact)
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        original = contact
        position = intent.getIntExtra(EXTRA_POSITION, MainActivity.NEW_CONTACT)

        if (position == MainActivity.NEW_CONTACT) binding.toolbar.setTitle(R.string.title_new)

        if (savedInstanceState == null) {
            binding.editLastName.setText(contact.nom)
            binding.editFirstName.setText(contact.prenom)
            binding.editPhone.setText(contact.telephone)
            binding.editEmail.setText(contact.email)
            currentPhotoPath = contact.photoPath
        } else {
            // Les champs texte sont restaurés automatiquement ; on restaure le reste à la main
            currentPhotoPath = savedInstanceState.getString(STATE_PHOTO)
            cameraTempFile = savedInstanceState.getString(STATE_TEMP)?.let { File(it) }
            savedInstanceState.getStringArrayList(STATE_NEW_PHOTOS)?.let { newPhotos.addAll(it) }
        }
        showPhoto()

        // Efface le message d'erreur dès que l'utilisateur corrige le champ
        binding.editLastName.doAfterTextChanged { binding.layoutLastName.error = null }
        binding.editFirstName.doAfterTextChanged { binding.layoutFirstName.error = null }
        binding.editPhone.doAfterTextChanged { binding.layoutPhone.error = null }
        binding.editEmail.doAfterTextChanged { binding.layoutEmail.error = null }

        binding.btnCall.setOnClickListener { call() }
        binding.btnEmail.setOnClickListener { sendEmail() }
        binding.btnPickPhoto.setOnClickListener { pickFromGallery() }
        binding.btnTakePhoto.setOnClickListener { takePhoto() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { cancel() }
        binding.toolbar.setNavigationOnClickListener { cancel() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cancel()
        })
    }

    // ===== Appeler / e-mail (Intents implicites) =================================================

    private fun call() {
        val phone = binding.editPhone.text.toString().trim()
        if (phone.isEmpty()) {
            binding.layoutPhone.error = getString(R.string.error_phone_empty)
            return
        }
        // ACTION_DIAL : ouvre le composeur avec le numéro prérempli (sans permission CALL_PHONE)
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(R.string.msg_no_dialer)
        }
    }

    private fun sendEmail() {
        val email = binding.editEmail.text.toString().trim()
        if (email.isEmpty()) {
            binding.layoutEmail.error = getString(R.string.error_email_empty)
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = getString(R.string.error_email_invalid)
            return
        }
        // ACTION_SENDTO + mailto: : seules les applications de messagerie répondent
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(R.string.msg_no_mail_app)
        }
    }

    // ===== Photo : galerie et caméra ============================================================

    private fun pickFromGallery() {
        try {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.msg_no_gallery)
        }
    }

    private fun takePhoto() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            toast(R.string.msg_camera_unavailable)
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val temp = File(ImageUtils.photosDir(this), "tmp_${System.currentTimeMillis()}.jpg")
        cameraTempFile = temp
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", temp)
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            cameraTempFile = null
            temp.delete()
            toast(R.string.msg_no_camera_app)
        } catch (e: SecurityException) {
            cameraTempFile = null
            temp.delete()
            toast(R.string.msg_camera_permission_denied)
        }
    }

    /** Copie l'image choisie/prise dans le stockage interne puis l'affiche. */
    private fun applyNewPhoto(source: Uri) {
        val dest = File(ImageUtils.photosDir(this), "photo_${System.nanoTime()}.jpg")
        if (!ImageUtils.importPhoto(this, source, dest)) {
            toast(R.string.msg_photo_load_failed)
            return
        }
        // Une photo non enregistrée remplacée par une autre est supprimée tout de suite
        currentPhotoPath?.let { previous ->
            if (newPhotos.remove(previous)) ImageUtils.deleteQuietly(previous)
        }
        newPhotos.add(dest.path)
        currentPhotoPath = dest.path
        showPhoto()
    }

    private fun showPhoto() {
        val bitmap = currentPhotoPath?.let { ImageUtils.decodeScaled(it, 512) }
        if (bitmap != null) {
            binding.imgPhoto.setImageBitmap(bitmap)
        } else {
            binding.imgPhoto.setImageResource(R.drawable.placeholder_avatar)
        }
    }

    // ===== Enregistrer / Annuler ================================================================

    private fun save() {
        val nom = binding.editLastName.text.toString().trim()
        val prenom = binding.editFirstName.text.toString().trim()
        val phone = binding.editPhone.text.toString().trim()
        val email = binding.editEmail.text.toString().trim()

        // Validation : on signale tous les champs invalides et on met le focus sur le premier
        var firstInvalid: android.view.View? = null
        if (nom.isEmpty()) {
            binding.layoutLastName.error = getString(R.string.error_last_name)
            firstInvalid = firstInvalid ?: binding.editLastName
        }
        if (prenom.isEmpty()) {
            binding.layoutFirstName.error = getString(R.string.error_first_name)
            firstInvalid = firstInvalid ?: binding.editFirstName
        }
        if (phone.isEmpty()) {
            binding.layoutPhone.error = getString(R.string.error_phone_empty)
            firstInvalid = firstInvalid ?: binding.editPhone
        } else if (!Patterns.PHONE.matcher(phone).matches()) {
            binding.layoutPhone.error = getString(R.string.error_phone_invalid)
            firstInvalid = firstInvalid ?: binding.editPhone
        }
        if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = getString(R.string.error_email_invalid)
            firstInvalid = firstInvalid ?: binding.editEmail
        }
        if (firstInvalid != null) {
            firstInvalid.requestFocus()
            return
        }

        val updated = original.copy(
            nom = nom,
            prenom = prenom,
            telephone = phone,
            email = email,
            photoPath = currentPhotoPath
        )

        // On garde uniquement la photo finale parmi celles créées pendant l'édition
        newPhotos.filter { it != currentPhotoPath }.forEach { ImageUtils.deleteQuietly(it) }
        newPhotos.clear()

        val data = Intent().apply {
            putExtra(EXTRA_CONTACT, updated)
            putExtra(EXTRA_POSITION, position)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    /** Retour à la liste sans enregistrer : les anciennes données restent inchangées. */
    private fun cancel() {
        newPhotos.forEach { ImageUtils.deleteQuietly(it) }
        newPhotos.clear()
        cameraTempFile?.delete()
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHOTO, currentPhotoPath)
        outState.putString(STATE_TEMP, cameraTempFile?.path)
        outState.putStringArrayList(STATE_NEW_PHOTOS, newPhotos)
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_CONTACT = "extra_contact"
        const val EXTRA_POSITION = "extra_position"
        private const val STATE_PHOTO = "state_photo"
        private const val STATE_TEMP = "state_temp"
        private const val STATE_NEW_PHOTOS = "state_new_photos"
    }
}

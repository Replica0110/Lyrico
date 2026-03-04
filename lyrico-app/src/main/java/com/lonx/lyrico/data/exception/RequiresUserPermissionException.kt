package com.lonx.lyrico.data.exception

import android.content.IntentSender
import java.io.IOException

class RequiresUserPermissionException(val intentSender: IntentSender) : IOException()
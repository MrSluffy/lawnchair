package app.lawnchair.root

import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

object ExecuteAsRootBase {
    private var retval = false
    private var suProcess: Process? = null
    private var os: DataOutputStream? = null
    private var osRes: DataInputStream? = null

    fun close() {
        if (suProcess != null) {
            try {
                os?.writeBytes("exit\n")
                os?.flush()
                os?.close()
                osRes?.close()
                val exitCode = suProcess?.waitFor()
                if (exitCode != 0) {
                    Log.w("su", "su Process exited with code $exitCode")
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            suProcess = null
            os = null
            osRes = null
        }
        retval = false
    }

    fun canRunRootCommands(): Boolean {
        try {
            if (retval == true && suProcess != null) {
                try {
                    suProcess?.exitValue()
                    close()
                } catch (e: IllegalThreadStateException) {
                    return true
                } catch (e: Exception) {
                    close()
                }
            }

            suProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "/system/bin/sh"))
            os = DataOutputStream(suProcess?.outputStream)
            osRes = DataInputStream(suProcess?.inputStream)

            if (os != null && osRes != null) {
                os?.writeBytes("id\n")
                os?.flush()
                val currUid = osRes?.readLine()
                if (currUid == null) {
                    retval = false
                    Log.d("ROOT", "Can't get root access or denied by user")
                } else if (currUid.contains("uid=0")) {
                    retval = true
                    Log.d("ROOT", "Root access granted")
                } else {
                    retval = false
                    Log.d("ROOT", "Root access rejected: $currUid")
                }
            }
        } catch (e: Exception) {
            retval = false
            Log.d("ROOT", "Root access rejected [${e.javaClass.name}] : ${e.message}")
        }
        return retval
    }

    fun execute(command: String) {
        val commands = ArrayList<String>()
        commands.add(command)
        execute(commands, false)
    }

    fun execute(commands: ArrayList<String>) {
        execute(commands, false)
    }

    fun execute(command: String, showResult: Boolean): List<String>? {
        val commands = ArrayList<String>()
        commands.add(command)
        val results = execute(commands, showResult)
        return results?.get(0)
    }

    fun execute(commands: ArrayList<String>, showResult: Boolean): List<List<String>>? {
        try {
            val results = ArrayList<List<String>>()

            if (commands.isNotEmpty() && retval == false && !canRunRootCommands()) {
                throw SecurityException()
            }

            for (currCommand in commands) {
                Log.d("Sudo command", "Executing \"$currCommand\"")
                os?.writeBytes("$currCommand\n")
                os?.flush()

                if (showResult) {
                    val buffer = ByteArray(4096)
                    var read: Int
                    var fatStr = ""
                    while (true) {
                        read = osRes?.read(buffer) ?: 0
                        fatStr += String(buffer, 0, read)
                        if (read < 4096) {
                            break
                        }
                    }
                    results.add(fatStr.split(Regex("\\r?\\n")))
                }
            }
            return results
        } catch (ex: IOException) {
            Log.w("ROOT", "Can't get root access", ex)
        } catch (ex: SecurityException) {
            Log.w("ROOT", "Can't get root access", ex)
        } catch (ex: Exception) {
            Log.w("ROOT", "Error executing internal operation", ex)
        }
        return null
    }

    interface CommandProvider {
        fun getCommandsToExecute(): ArrayList<String>
    }
}
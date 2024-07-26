@file:OptIn(ExperimentalEncodingApi::class)

package korlibs.image.core

import kotlin.io.encoding.*
import korlibs.io.compression.*
import korlibs.io.compression.deflate.*

object StbiCoreImageFormatProvider : CoreImageFormatProvider {
    override suspend fun info(data: ByteArray): CoreImageInfo {
        return StbiWASM.stbi_info_from_memory(data)
    }

    override suspend fun decode(data: ByteArray): CoreImage {
        return StbiWASM.stbi_load_from_memory(data)
    }

    override suspend fun encode(image: CoreImage, format: CoreImageFormat, level: Double): ByteArray {
        val iformat = when (format.name.lowercase()) {
            "png" -> StbiWASM.FORMAT_PNG
            "bmp" -> StbiWASM.FORMAT_BMP
            "tga" -> StbiWASM.FORMAT_TGA
            "jpg", "jpeg" -> StbiWASM.FORMAT_JPEG
            else -> StbiWASM.FORMAT_PNG
        }
        return StbiWASM.stbi_write_to_memory(image.to32(), iformat, 10)
    }

}

object StbiWASM : korlibs.wasm.Base64DeflateWASMLib(STBI_WASM_BASE64_DEFLATE) {
    const val FORMAT_PNG = 0
    const val FORMAT_BMP = 1
    const val FORMAT_TGA = 2
    const val FORMAT_JPEG = 3

    private fun malloc(size: Int): Int = invokeFuncInt("malloc", size)
    private fun heap_reset(): Unit = invokeFuncUnit("heap_reset")
    private fun stbi_info_from_memory(
        buffer: Int, len: Int, xPtr: Int, yPtr: Int, compPtr: Int
    ): Int = invokeFuncInt("stbi_info_from_memory", buffer, len, xPtr, yPtr, compPtr)
    private fun stbi_load_from_memory(
        ptr: Int, size: Int, wPtr: Int, hPtr: Int, nPtr: Int, channels: Int
    ): Int = invokeFuncInt("stbi_load_from_memory", ptr, size, wPtr, hPtr, nPtr, channels)
    private fun stbi_write_to_memory(
        type: Int, inp: Int, w: Int, h: Int, out: Int, outSize: Int, quality: Int,
    ): Int = invokeFuncInt("stbi_write_to_memory", type, inp, w, h, out, outSize, quality)

    fun stbi_info_from_memory(bytes: ByteArray): CoreImageInfo {
        heap_reset()
        val ptr = allocBytes(bytes)
        val infoPtr = allocBytes(4 * 3)
        //println("infoPtr=$infoPtr")
        val result = stbi_info_from_memory(ptr, bytes.size, infoPtr + 0, infoPtr + 4, infoPtr + 8)
        if (result == 0) error("Can't get info from image")
        val infos = readInts(infoPtr, 3)
        return CoreImageInfo(width = infos[0], height = infos[1], bpp = infos[2] * 8)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun stbi_load_from_memory(bytes: ByteArray): CoreImage32 {
        heap_reset()
        val infoPtr = allocInts(3)
        val ptr = allocBytes(bytes)
        //println("ptr=$ptr")
        //println("infoPtr=$infoPtr")
        val dataPtr = stbi_load_from_memory(ptr, bytes.size, infoPtr + 0, infoPtr + 4, infoPtr + 8, 4)
        if (dataPtr == 0) error("Can't decode image")
        val infos = readInts(infoPtr, 3)
        val w = infos[0]
        val h = infos[1]
        val n = infos[2]
        val data = readInts(dataPtr, w * h)
        freeBytes(ptr, infoPtr, dataPtr)
        return CoreImage32(w, h, data, premultiplied = false).premultiplied()
        //return CoreImage32(w, h, data)
    }

    fun stbi_write_to_memory(image: CoreImage32, format: Int, quality: Int): ByteArray {
        heap_reset()
        val dataPtr = allocInts(image.depremultiplied().data)
        //val dataPtr = allocInts(image.data)
        val outSize = image.width * image.height * 4 + 1024
        val outPtr = allocBytes(outSize)
        val res = stbi_write_to_memory(format, dataPtr, image.width, image.height, outPtr, outSize, quality)
        //println("ENCODE: format=$format, res=$res, image=$image, outPtr=$outPtr, outSize=$outSize, quality=$quality")
        val out = readBytes(outPtr, res)
        freeBytes(dataPtr, outPtr)
        return out
    }
}

private val STBI_WASM_BASE64_DEFLATE = "rHtdjB3JdV5V9c/tvt19b88vh7xDsrrFyJS08q4UeUjvyiJrmZmdJWlaD0ZkBHC4qzUVs+9wuTO83CiJhBn9GFlBEZZL0s7a1sNKCGIJ0MM6yUMSJDIj044dOMAicAInDhIF0IMEyAkD+0E2BNHfV1V9750Zjr3+4eB2nao6derUqfNTdbopnr9xTQoh5Ofl7HPB9va2eE5uy+3n1DafbGCFzRFgW5OoxITZGvpGIZ7rAPK10HUKgCnBtj3yoxLfRErjzmSCGPzUR1IwFcs8TmW3EwYqFDIoeioM47AvAlXKIJJSqjBJpAySLIw6QqEeR/gXd+NEKYWOIAilCgI1E4ZoCLI4CiP5kux2o46Un1Y7OypOsFyz85th1vkDzHbtyrXrW/9Aie7PXnn+pctbV25cGYkkvvb8xsb1F0QafmzryhXRWbgx+ujVyxvXn/+Zyx/bun7tshskjrn2qy9+7Pqu9r89b9v//tbV0ZXLo+ttczcD6tXR1ec3rv7DK0Icvozqz1zduvLC6PLHbr74wujq9Rcvj57/6MYVib4r1268sHX1pdGVFy/fGD3/wpCsja5vXRHhof19jttoeV/H37syuvzCza2tKy+ORJz2pTAyS0X5+BPvO3X6R5/s/tLX1EeUyN4jtArPBme10PK9QjwphAZiU6HKQmpl5LBWhciybE0Q69mQyOgoZT/TytaUHcBCkhSHoM7CE50mU01NOZlvGiNG4wmRleIdAIbmpc0aVS2yEA0ZmC6E+d5vCG7kCmoXpdpmnb+TStSSHVoOaqlJvLxeq6EpG7I5qEVTKww6g/aRVj8O7gHdNN3mrDiSaZn98vvUwnbwyXcI88Y7hnUH03Y0Zrkn1dn2T4teCK2UYaY7T3/uS//m3/7uf/j0F7qngluLRlQR1qFGdWaExu/8oJrRAeDAwnNok1v1rA426rxMq0JneHaDs+Yh/nWqHgik+CU60nPPWBmlJgIA2ULUaE11t8yAEeMnrAhn2B0D6DaPCVGXujTHb9blDV0Omyr2ws1llgGpx996LQZVD5QSDSDRKTFSi1FghtmNBnuXcNrt8bQJp9VJZKKs3THdBfPo4+jIju4YsaLeqMzOTohVzaD+RtXUQChz3TmJnlqYEBqhJXcpxsAVRY2KG/M96sikvQZrJhmiegmL20uhxaqSTLOukzGd/0U6mFiuqFuxL3uaLbd6TW3LuKllWWCfOiAJLJBSDdF9Q89IHY+2OBAtEQYDve5yZa3MsUK5BsZyEwyrOW6SngOjQLdoqOb8YcNpDVKn2CK1jaaigTVgDa9KKJ6fPjIJ9slNDdJPNCYpezoabUGmY5R0GmVHbZnU4WQV9TP27Asy7fdTd92i3ZKx4C73qGVxvFZhOqDkphF+1WhruFBPMd5Pyg+Q3lHAZo2qopY4LKTLjaYx6hxS+sFD2UDD5TUTbTSQ9sWCKkQzpK5jokTnlwoJ3GRYx8Rcr1KrWbUEqVYRnEJNKUPXKwMxK5n5WbWkOkSwBx1jp9Q6VcJT0eQJ2BenqWWP0M5orJ0RyJAlnQ7qeKylnjlxMHNiD3OWeqrlFNXkZdJL/9robd+o6VA9FVI+eLFeVqIlOZaZ9Gx1bV+DvSlAu5TaIesYJr3CUiewwFxkIsMWv/me69ZzZUZWCcoYu4r29fAsKuDE2B8tAs0FPIZz//RdCdmnYdDs4z0+K9VZU2VY2/9/GLyErsQBCSZ0PovKBiKtKnBoHbXbvV7I7ECtsbNEXmukjpwgu25jpnVG/qV0ZrdDi97O1saeI7GLoxh7AI7+Wum1WgI6nqoslZbmR5s6LlPnKBfpiRTQXslXxBLwgifFIRaWmUULlYAWLLQEaB6QelLMsbBIs4Qs0gwhi1Rq8fTOzs63b33u9pf/izglEroZc7TxrgcOfNm768AUZd/5qVqZ337l574crYhQK54aAhZ2DkXIziEJ2Tns/E3NyMV1dTS63ASBlmOaknzs3Prqa79yH3xgzaTo0YwgmrknG0qsLPNOREWmZusZ68nrXjkDvyA2Ee6xH1aGDNRW9kCj0mKoVfpZBoRhVeqUDmlY9U1gI3hmrWCWVtAHj4KkSAg2N1wPaUEJ/nq6LGdpG+uDii0xbQ9wnHlL8Xbgp/fKXa6xigbdGM1AzWZ2G4ZLk1TCDQdhOWUtkQnL3CO3tHyslW2sbVnVPVTUtj0TZauF8BJKIKGeVdIpCe2RD+klRvB33h5CgCiHTlDwY01Vqu2AdIGChfjIG2vIBpTJ0sjsJM3jUlwqBEktXihkG5YYvyIfLl2AkwinQaZnp4Xc90Lu64gC1vH5IpyWqKcGUU2oJear0gVo0QbfaFfw7e45akQIlxLDlodm4ydoqp5qF233Enq9XWcP7uxkaUPd9SGZ3teSyqAN2bBKvEASTbfTCuSbE4F0xtxHuyeITETuW7q7I3vd5f4/W6gjEwJoeSPdz2iio8mUw5ZgLqmIvV2nnPGuJJ7C3rOOHI+dQk498r5Tj8dmoImzvUeTrOpChKk7z4pMRHrGbTiPpu1Nw3UxMPyORIe5L8oAFbWi7kMe1uox+W9JLVd1sKrV6tZWoYw036I5/w88BnCbEaqXBrWleXFQwQzQ9D7ECPPKD2kBbQ42X8REBmS4N8OsSttju7J3H3NPrIXQcO8GMpPQDWRg1jy4L5p3w0aW2wOn64vRV6fO2zfnhNg5o8UUen1ULwPn1m+IhvJPgPjNYw3hf3n37s+fVoIjjzc69WOJnrBtN1Js5J4YnBHMCAL7/mdegcb/s0VcfXY+e/+VN9+4v7N9Sn0RdfMZ9ZT4xUV4Fm4KQsxdNj781Gde4SmccG1mIKTfF+uDJ8Uvo4HB4nWUPlr8U4AMMr9gS9v084s6NG/hane4lE4X7klzGOq+AkALt0RIcqr37ND1og9xYvr0b+QEjTYTW7SYIthPpO31kXBpqnNpPNS8dtSUUz3lpOdLR00+1ZP7HpAryRUIH8SP+dUDib511Hx5ahAqE7LyzyP7qeMHkX3t+EG8zhxE1G+A+dI+om3PveP7eW37voMd7U319Rp2QNv9JfKLx1z5+jEtjWqqGZeMmNPK2CARsCh1BGu8aMNOHtCOUh3o7GIR2sChdHrenz4zHjd5ul0rlLb+PEGtBHZ0fqDVRrWg0yq2sdgamqCh9Z3aNHoG658FuRi4Cw275q291MuwI3HmrpbaNxw998P/6bGv39VzbcOxc9/5pa9+/e7t26eVDwi63+jlc7//c//839/VR/34Y+c+93f/2xTWosf6rx//zo8R6x8v/uKHiPXl1cUzwOKoXyeut1FvsbRPfxvXSSXcwbzLxXbpXGjBKtMpoZR2LF2Q+uIxBCmUr6NslT1xPgZB/IE2D54o5ypaUeSMoef7jzc8ppgHJ82D08AoJhgxMRKdj/tyMMO/SIuJe/Kex0jzUDqJZ+bBN0RjLy4b5v0NHOy1Otc5+i8NCNDR8mznPZsfilH39o7C9G4UAT9qOrCbN4801Tx5RbnA+mJTHaIGmbIqrC7l0I70YiEDalLealLqNalwalLu06RFne/TpO6jNAlippLt1aT+bk3q67kDNan7tjSp+1fWJFYPVJh5U/o8T+IVYAEtLXxoAjt2bH2pVauDymqJp0j8upBkl8GPRTSq+v5mGbu2hCJcJOWwacj8CPJjW+vyI8ZFTbULt+rDrNxGjaF3qz4yqd5HdWCrjIwfuuulpxoNATpxT2iNyYwJ7B2aUHTJtDC7BLsWzKk0+Z9lgOG0gWVjI8omclTOwHrjvp4lXZB0oVMWKU3Ob5ubxE1hHvJAPM/AzICx6Byw25z9EVL6MPDfHa7UezHYD7dC2ou7j1JAHx+lcBBQOtgyvGHg3PAGr13yVPCvSSlYUf+KJYa8yU5xKviKL99gibzq675+SyIrWw5dLpgsS5uKyL1q/V8obTmwVgkEGylym4y1ahTY21DXho46QI4zMyGkRZunidPWlYZ/pPr0sH08Xvp0b8rslDCqitkNO0Fbgj0rdK8pF2yWNoZcL2jMemmrgC8AIVo+elcLtdu4dMR5I7s9+wYmk4Go7RpnBxAeVnHmbZs17ONmuTjOBscIcb438aQOeUKOgt9pO3HjBtNZlYey9uBMNI9XLuVy7+Y+/SfYvXvFKTXvdm3ZbaJ22dYTfn+fOBWcdLv2hCtOo//skDduDn+IFsEj5A8+s/Pg+KlgHTDav9c/FXzY4f+UK55zxc+64iVXfNwVO7JVNdS/geFvpaTKPioFdW6D5+M/4dUlmVZHt7pKOH2KTnBHWw2llXWIL8yvYe3QqMgIallgBBOXyvDHW2tKtVotpNcpun+f+qIorfvX0ubJ3D5MewRRLjGdFoJ4DtkSFjPC71FEFUbpbRNvIlDV4TD735GU26SKlTk9DLnAt+bNg3kdDOy1NrShKkAPVYUsmhC8KAA6anhbn/zpuBcLqYIw6tC9BIw1eTQGWWo5JDjMw0c1G/nyMA/GPWZXnyiXh7maEJZjMHsSPx1QHAHFsYsnMj5hy4QVFuBeqkhmD3DZkjajVSmbXA/Wio5d2pg95cohJvFzUKy7KCkUpEQSiiRiS2LPCt82FcdP2BKLDiBGIGTZQG5vh3A4XihHrhXh/oVOJqhj38gSsj94goMlGTxyAnD7lyGm3ta2MAJbOiUU4SMSCrrtstJqFZqsqDWKmcoAbcw4SORdSFnC3kzSlEcrafGBrSUtlYrqM3mCWCiyr0gZbjtrD2lK2lq0wrAqsCaSrK9aGxGg0qZPA59oncqOhSZ3idbwpMrraUTmWSXpxXZ8m2wN2kyw7cSdls3KzmwXhar1B6GLWngfCwunAC0fcpNxbLRVKVZuVqBDq5Z2iMre4KrMJ0jcfOLigIElcLILoQ/cCyYtpdke74kVko7cftD736D/uLUAz+Fytjcq5V/8Ora04luFNqtK2m2y1VV8DtZXmJd1WOCFcj/SjtFq8gZB2a6zqD1Omu8VypSjLUgzGGlxA2X0shYNpevrM75+2Ndj1rMfh6TMp9XEIdeA0XDJmrXz6HyH2eqfdOVaASCC9PrZtBreiVV/W/K97y01rHMqCfipUp08Lr/Rqfos73WqkqWqZliIajbgW73sInZBVNzE5Y2qQLG0UfVQzG9Ucyh4Y8SjWgCc44rgKB+y59ttnm/XOJ7hhNcz7hGtXacMXo19J1ygq/BdPEDqrL1HEQUG09MFIH9CzTx4t17iCf/X+DijLSx+enC7Xvo8wZ/4aRBb+gpzVAzJH8+wKT7VowsWBTVg6u2wYljLT6qy7q352KZ0B2LgQ5XzwAEf8RAc5k15uEXQ+eOyVwfkqQ6JHVps181ORNZyPjNnqpTun0JATHXLTDnK2TYZGTpGjHRGiws+OihGHVSZ3fJuheXXoV2FIxGShLPLEAMK4Q/jOsYsVsODIbgrL4GSrAoez14GAp05+gqK3LNa6j7YnhJNYPqbFYJfvHvFCTriUfP21q2zKvByPrMGhnz3rJ6BTHQOZaRb6DGidC2pVKfILuBxiI9FPhb4mOOjx0fRlEembhwcmtmhXbSGDRWOj5yPko95Ppb4WObQUGc+3Zd9VcLGxock68J0YHObZkkr1kJbwwqHoy3uChoS+87evxzqtaecXmOkP+WABlTu+51N+1wjNl/79FvU/hRqRoIIrXTRIycqGrLzoiELf1h6mq4wrJRLrjLDEGjJIsQE3qXt+dJE5oq7qOknEYayD2CxnHe9VuZsY76+zfS1P6itV4p7o+ydZ51QQ3eXZB/FvHaiKmAhq5CFqqKW0oeGB1AKxpRqxbRnlyt6UqQoIlDWgoSCcjF715gvSTIUkuAejoWEqgCtLPuwVMDUgl+AqBtaDRGkpTs+GPUsVZkLYPhESB9LUz0lFQqO0qIx21qN4PQ3n5Iieyhl75OM0HAnzGOwyO7UiVaEch2xKO5AKyWhvo5ZlHfq3u1zfxj+yJm79cwdfu7CVrhCFvN3dHq7Ts/97ue7Z+5SZ3WC6p1zC//v1z90F+PqBZuRCGADC3cISVjUzG3MmZ67+WO/gjHASS1OR6PR4gid6+I2GOrr8nYNKGUSjoO6Ortdd2/XmR2hUE/If6ILnd9pmeyyk4rvycUaAwllvyql8hYQnBD0MV6rVgvRalVoNEx36vz6vmEvFDLkSSSgmUyUO9il3LlqEZ4SRYtQNEaNEWSLsCLSFiFtTDgxD8Wor5w/cpbwUSqB3VAtG+gADcG+o6ZWyBuuErBDaeU1hZULgzrUoIw+May9FokbroJe0rsw4KEhm6VGyjIFBYFSapX9i78jj1FQb4H8YfiKwzxi/TupDxuUb/rypCuW2Mt7P0vd5oVdI5ICqjoOh651WA10VM0bUbXvXkOG59fAbMIZbAolMMLkZaBR8tIWPJ2cUuxLmrpTHq9ssuJdmOqU+pqcvmb496UJ73pP6HkTXiigTnwlJfR8qVeLjkkwOjmpzsKIU4aHEnVlymeKhLsi4XzY/UTVZ/FBBJz51SLSfZ4MZ/T8moPDSrLAGjTJuEOqlv5zsEjPb2i5YeSoTKtUz/AINL9lukMb8kM9R+fV1/M4reYptBNN0UYNDjmEukdXMuMqSkd1WQk8EXbqbr1Q5erswX+66B2RKhD4F4ooZpmkosOym+Uia6ctmaYo6TFLXo9zrXjywRLo+b3neEqyHjZWQAqs+PSG/xKQ4+hoOLL2Y1CELNzQeP/QHof2OLTbDhXj6cJ2+ngyfbKPhr+Yk0bqaHhU5VDDKdQuUbtETR45HQvP6cFDOwcPdT0KHJPV/UQWSGSBRGKHumuQYhlWYs+ywwmt+JHLjjwF8wtyg4Aw/1lsNJaYObrR4PA+FsjBREI3eZeFqh6lALml74l3d1EOPeVHyiv4i7GXPJKIentE2BCDXfK0n1xOcjnJyUeJ30Ghk3i8Z7gO7YHRwZmG7dvvTqoq9TbEE20njzMjeD7RnbI23xDPFIIQmHXQO47gccI/WGXkJgD7ydcg3JNrW3ieJvhdsYZ30gLQ98VapbK9Tk4ruja4aqTnMKmNlOXfWC1Sl7oNEaj7k2gALCyLt4DkAlCAIMaVBBXnBpergMVSdYzFfBWzKKtFFnl1yCeWtllN6tTk7th16Hyh2BTWOQs9NEtDE1xjYJOmf76QbWdqu5uhUTezusvoeszwWrJWlf5DyK9IPr/GJ5Y6bGplPpUMzc7Hny2yloqio0ExROdOcr6gZFT5zpZWLarSLNmsbxfiXQAPS5C+CaqIUbmQRzITmkAfG8Bd8zZQZlXovvFIKEe7s/NG4a5ZWjnVyslTGlH+EOVbaqVZOVlnrBamz8/GFqCuO+r8VtGlCmg88XNfw1FSzN1Sw5gyZAjSyt0aqB+8XHtAtoDQvBERPkLlYyY/8Ce79iNP80nzCUt7sKHzZizd8p0ukrrdAnvhsBeJoihklLmNl8xNeYmLDHu1WeXAS56x3y9Y/gP3Lqzrlt675F+4coAW58GY0hkTJ1wKmazncKfSc5xtrQ4HXNv4s/BIAwL/0kGKy+XRGPIvkbesJRcagZ5dpXBO0/PK6LL1TIGhzgRybLS/96DOQXXPMZHijD6H2xrkHg4i8uRuRMpz0dPKcUHIcxGRC6UtA2rCQEQGlE3Ttzl1YSefkrHNYRWmxH5aQAMwAgCULThieadcYZoQNDpi0F7Ti2bnB8Hapj5kr0UAws3VQsENZPbsk+MX4pdiFJfr02FqM6uWcZnVh1b1Ir9U6epD5bvwezf2bbF8FwS5CDjUMWClY8CpPgaYXju5oEM8tnSKJ1rfXS+xSj3tDKtKRyyOasWihsHMagkwq+bs6qqu3aNqximFPjJWiAAtEVRiFsJDR2S5vVQopxmy1WsPyBYQkANfNTvdrnqZUwWpZ3jHgoJgtO61auDSgD1cO/3LSrsZU9PJdjo4J911FnMCEqdTzepiU1dQmfe47FyhFzf1UR2yTn5ijf5ap6wrfQzzF/rYJpR4qXxPBDazqngUc8Vu5ordzFFPlqGkR9hyhC3TIeE/jkPCb4+h3xxDpwF4xFPDXiCkkJmt/Z99EUAr/7aauk/XZH7vlioDjIaCnq6PWEmA34ER5WOrBVyePcfWisUHvC+jT7U51trmeIMyXVGn0WSBDwJQBDxJSXQeglliPLACW5+ANAAwQfFzFjvjRuWP5Eax/pNGvWw6m/bqEazBTwx7SgplZTR1apbOWTK/TLpaMQ8WoiSfwjLUgEJYysw7RUr6QCryYCp2CVo0+2i5DuLYddgVbbgUNgXv1zu1UMrA/J5oqiW6SusUpano76V57Jkip2ZW7pnaZ8SnjuC763Z3zgMtberAkmUl0TnmZPj+3i2FWojainpgYTR+2wLKo+y8Zpt1ig1dUd91SDrYYN8fOUzUo8GK+j5rZJGI32IFM3I8WA5GFJq5JzaB+E0/weERzhJwaCvqf95SFOU9I9apY1xzgkhgFF3I5oh0Pmy23V7LzUH5XqrDEhabvFf8LbMj4fCoSIy3UFnLQ2DyC0VuwOqoWiTB0n6p4d/cLFKEh3y8BGcMhLZQ7fFUTAfGRYLL2ib25xDMFKjaawZ9pVFNXVSB2bZuVuEX8Q5W9XVAZ4mcbxcOUFBf7dyzXGPkA3EdrRZBRltHMokNWo10uVWVPCj66TlmWDGROLMJNy6hKGBCc3I/L7pmb1YlE3tQbxTLCG8BJRnBbdhpCpF5HfOG19NHioC1db6fDMj6lOLVk/98FLSvp8IpjSf7S3Y0ReiPEiTtZDrV9yiDEsgTUafWt7CdRQGifSPcHffhmfNFH1sjMZZK2+dWBGjoswExthbcpQAIkv0pUvXoTX1vpPmS0LqLVMsaXPcyH8ttxrH8YSM5vZ7l8HBERmZ0AMmuMruo1zdrOYBgQz2rAXR16g/o3of+saQ/bS2ROUKn8yvqJy1wwnQ3VtRnYTTU2VpyWZSUnIgpo9ZSj5FIdoNpSx8eIwnybz2zLN+ZBwAMACxiG1hZ4OdGiPcY5C5jKFwrUn3cGz2TDNr5ZUF43vyjTe4ZFp93gW1CPa+VFmWFhtTtyeu3mJqRxkGUcICX3rcksfG23AKwsH9CQHdIVYKz9+/NlTA/4mnFY1pxSytuacUtrdjR6thEjMuodNH7xS9AYxaNoMclB7TYRQC/hUsa39Q/Mf2+CRNB6paqKN+/WhD47HfoNwn8AYBZAPc/0FR9cgBjRvFHr0r4BB2Xf5Nz7E3yMGFVS3zCgqScFCpUmAXHg/IDOraA27YHr1qpDIltvi+fLRLbWXdPYi1yveDxvySnIxM3yADxBlrWTPbq8mIReUrufdtk5JT1KXIGbDW9fptB5wlWIoQ748zQqWxnLe3LiBVVojxzAT1s6G+S3XluKpsO6dLaqVWUkoIsMT2XUzG97WXrj6ns+Z1XJcTqPpnUuWUVjF4qFJq6mDt3cxc4yppOY4KbtX2rb9eQajQv72umm0x1132lFYys++zZc1iPbUsclZsOUlQBwQ/yAdQN3WtsazZWjnuCAzIr5xrumK8IpXtzjHKDLmSiLCTzmA5ZnKjBs+5hcr5u5jqpkcyM94je2/cRFsE8dzKB8f0Iffe8gUzd3vW4d8IE6wM9P4CqCf4ZqbYNy+CZWhQChzgif/dVjFpjVbD67VclYELfgqCzemag+7wvajHANUjwMhiXK8zlJVD+IHGFGAe0OQhgogGhBieYsHGnL383jOBQ7c599guSL6LDa3XALINte8W1JddqDCnRlhp5kynXdmGMHTnNbBmdwqV5ZKODF6lVOcc+RlcTAkaRTAypp4RUmYlNxyT002sD0l7D/udoTnngZwMObUDkHtBPoFNmJuGVLWdY3xWO+3Z5fIRG4sosbP4UYnc3dGfLjnEJHN29QM/HJaQHCCw/uVtMvM4nOkPaoo5YlHUIcawjNmxxZnBmQu0q7J7nI+dDzEQC6pr5/4VIASY+JELWwppyuF6QhGBnxkEJ9yF08LIftwxEcKUvEVcHhE80kGs2LQhepvsbOJs1f9rcl4BJVVz737W323f6DjMsMiC3GxQMGkERUVS4xmEE3JM8E58vEXd7RnRGxMSY4B6zKYlLTOK+hRhjUEBREVHRaGIiIiq4gUsWs7lvicr/9zunbnfPOM7Le/m+//cGTlfdulWnTp06tZ06VbfMjjfUbU1ftS+B08AHB7kkbqfWRdzcrkN/ruzR8WSM92XDYH8qI2URbNa41Pn7+gDS1Vt2pH+sz5rMYkUMUU1HZkZ7zaao2eSVJCPwWI04oEYVQY0U5OqZVryUlq7kdHCGtgaYgeBXaePrfP+vWYrix6mNvb5RB6QfQZxKqNFHXMIrjl7NWWl6Srra7zCN8k9zEeqBEQ4yP0kzdZmpWydf3tv9v7fT907/7x0jCYboAamldVYDg/16FVcczQlOn0q1BmBKswiao5WrWjStY4vKy4SnnHsxRZXTWh2qb9VaMypXJcVoXZmzlqxxFOqNzk/TlOnTWvZSpGVXCqLl8iQ7VzPy61kY7XDjxFRbio8iyWxDixNxBqa+3qLr2V7QPw+KxjTU/0RSXfiiPkQ31YmmeqJvfUsxvH7CtWD9vOjLzV716NfWth+nuE4TfdHHWRq7/UjkwDkhSFkk7Pq4eDoBx7d4VK3XtnQGC59HX1t72By3caRlUI5DLIKLzVoElbh6EVS3X02pr1PWSFJDdLvfcrq9JNHpWyKdF6hfZ4bGKXO2Ci5qdBQAyoKih0g0PFhzAYLlBRcN6xqeKh4naQ5WwxLoyRxuPf3jmKpMRYxOxDzusmCd5OOZKCo2nkjw3j1IttGe3UOpS8J9ka8v88MYuKmf8zDtnSbZkt9Ta1M+7ZdrGchKQXhqZndDWStmihfUp3h1tUhex66+wb5L65gyp3lYP9aneY4yX06nqbrJjUcQRZQsDCHPJfpbgUieqD4s0DImxoBp5oTRbhz0A53KBZzK2exJOJUjJ+pTOeXveQvJJbtzMn0sG2ZUDF+FadhQ8Ue7gyXbWasR8B7m8muw0MuSR40DbmOdU62qpRMeeSLM37OhxHT1dN7IuCBUMIyKeuV1PBKabnI0HpkeKA3IV7UpN2lVCQg515Sh2aRgSoaC24ZrPrgWNnAt7IdrAWfSDVwr6AQ4FJ6JvRjabxcZB4KLXWg3hol+nYl1HjSa36II8I6k99/gcoZcbpxmN+6LcUlQF8WQjKuzrDK8P1EsxMP7E8X0qBeKP4M7D3UjfUghk+zGHzAY8yldX0Sm5elmQiaOwOzhNWYH0R6hVyxxNjqcXHckGNQi0Sa0KeHes/A007NeG5Uf0j7FYb1QelLelFLeOAtUkFYgVzcZMj/5KNtd/bQNI5gaIyuObElU/L1DrGnZisBuhMdiijxvMtOCVO6dIBqtJLBFpwOvjWptYX/dPC9dEoHfSNgUTau0cBOzWdKVXc3CAcnwvSrncTOiS8PBCZYOodHMMMs8IxLGMCqrswGwRgYrOCVaK6TrQ4TTQEFQhqSYjgx28gF7Sp8rTd4Hg3i4ZafS3MmeRAnVvWUo10ioo7UoJnWc2w6iwFSSEg2/QPfsEJhTwyQSzqUi34F6viKRdfolAklvpuhHe7aVoY0re2ohqATSJmY/YxcmZWssVqVle1tUwWWbyTnHBj9Ze043/C2xQ8xqc1pF8Tmz8EIbLyp4YrKAvMKBEuz5qh2+x1BdhafWlTqS+HhLenvzyTdW1IbaWSH405dID0Rim1QptECPRwrdbvg9Uqd6h9glhY16gF65/i/ae6Vvv2pMRE3pXD190NS3G2Vt9deNemk36qXdqK/dKJd4kaaVGsqn3Sh9HpW9TEkVdtwwAeTJC3YBNh2JmsOUCpOCLukL0PLztZbvo+XbxSgIjOKksc+i999nTWMfq5qItDPERLOMgS9NCZFRTRVAToWnsmpFnwkt9VRUSaKxkIWTvrfNe6FTXn+IFmMFNG/EI3FnYKzIfAJqyRrI1AAjGLKhbHSiTkIU9MqKFI+6n/8uEjLvxCvz4J+nammYaNJGfDbCwaN5yRlnnZ3rDm3VC1Bu30EyR7LLxpGkGUOhVadvLo1bWTZQM9N2YCuSRyanhnzae0K7MU0g+Fz5DeW3wIzKjvy2SIhVt7WQ0Gb5Lck7uZgCRmF8UK/FXVwxyNc9tWgv9i6BXBATF+BkYRfnzjDvgsqg9tDDFLhZp74lOm1I3hYPgle1980YbgbpRluh9jCu4uERXRws4KFX5oYQ+uWtdatesukJ1eYsJIZBXeW64qgkB79iD6gcYGDfXGyLuaawovbhffT2LhGS8y3tYcaYSLQICTMqbntYlDw4Oy1bstpsEeTA1GHWkry1pH6kTIw1W7qidmDjONrcVc6TOTNrhnsFY4ZqUc+vXb0fWmZTViQ3O19XTlZ3On+Im9pjhxsLiITkTUWHjcjq4L5wqOtW1EsAs3hFgiSsxUDnCnHLzDZyyNa9GhQATq6qB0eqvVcTWgyWuV3x9k5D5H0j06CmA70FGUGtTT0VyidZMGnvbFTVQ68l5ku0pjChxfZajcYltcEwCtrBWHQgk8FdXB7Fg1m1qFXWgOkOjMWFh6k3cgPjXfb/eNK88ZinowdHTPU1EjSY3sFmbwQy4M6syZhWpW4peYaTLuhRC2fgpMfSnfx6Ofk0qDbYtLZDNlpn9LCmPXop7bJbwc4WTqHX3jU1o1SQ5jhMsh9VoxM1d+BUcL6YpIRqeVDUTTroFweTyAaTFCFWbePYpvOCAGcpsmXLbN8xUm8jkdhiq0scnEXxaoYhXs0wxGs0DPHiooc5Hkbd3qYhgWEuDYNE4SrVR95TjT0Ym07gOOii7VHNWiesG65oNUjpYpetvUnwcHMZSFTG1ErN6WUUg1dp2lJiLkUYTLNC5QhPD7G8kD+VIaXOj6MuSK+H8AgiFvt4cI0o852OsQ7nfRaFC0VRUbMMfyAD9XpXHvt9pUqDHUpCREkIQTl9sSTwojK06bpvXvTTyNK/lpF2VGy2wLI1BQLSt/Exh8eiw3h5xiulugb6vHqfW9KYmKYQX472w1U58Gmxq4Op8Dhnta2diFsezYxjpXcUHVvNvlDXrX7chpBAjCK6KvVjoHiG8e5ovdBMW+to9jMyTRPpsXqdwN0CI291DCIFO9juAnaMIpGgAYnhrMxXzXRNzvZFlRmhanh45OvJEfYQMbOeivN+QJLhNmMTnal2Uc8BwOG+mscigmCaQaOcyMcjWiCD46AXlR3IvanLbfzHMEzBHj778p945IknR6FL4IuJsYB9d7LuzO//2qt15cna867mI70PLLrlTB8lgffxyx6+JoteFN7Vl12MCNAIe2GopITNMim0ZDJHrVBGRdWjk+vii0hlX9t9F+XOi/Jcudwkp+thQQ43xvMiuuz1YGpOL15dJG5cqDL+pbm4FGaQX07s7qjTofqC8ua1Y/4advTQBoNlh6iop+JRl+8ZS8iikFJ7kDkLY0SJHWP3+ZIzYayFyNFsIuK7KMkgojRll4tet1yUItF+3C5iCItkyzxgZE1iErYkdpqdjMXkknIm1x5GcZEDNhI73cQ8u4d9jTFPLII8riHcubE/N87tHzYVI65MAqLg1WOgqAS/a/bQE3cuFuBuF8bksCQHqoIZIWUpqsZ6bimXDn3GY9PhvkRqKMCTKk7DMTpmV2A56RQhD00ywiceKQTPi5ESE4DrcQBmMV/QRg55auJWLPlCgi4INF+3Pi1Pe5RcJSdb7wUtnFyb1QHqmaEIWJZB3GnB2p6FBvimBqhjZKRWFnN2jHLg9H2IXDMsuC25TuJSTgsm2ZmrDk06n+l8SVdEuiIWE5JIM3hQdzLqqvdGDAnPqgRtsdcT5mvUcgLQRCtC0EvhyGtVwgJXiydyHBf2po0F8g33NQeBZG0RhzB4QJw8nH3b4lBNPdxKiKKUXUbnaiC2JAUGm5GyTRXwSJncX1Iu6EmaCNZbxVAWC2JUwUYkI3zAPSi2L95gpnmyvTGSy0gcGvVGN07H5vUko06MXcTuQRXE3QgrdcOAFGrLjNnhvw6Nk33UZXCTy4ZhsQd3OUZFvRLhohz1DHY0s9c+vhPN5LEaJ5AdfKSt+LQWsz2TJEaC4fqm7EuV4wFZiYLGx6DYLjH/Oh6VDOli7bAQufkVTylCJN6IOBkIGhduvp5B4H4mENO9FHVJDqP96YTRoyzMJHqzXyhc8VX1boOa3vk2Ys9sb/11fMWm+/fxJ7FddsQ2pQl2mJrr4hx/F+UqQFWd6S0wpcVTNAs1lhKmkfyYahe8AkZ9g0TaG2qEWCoNHmgteLtImFzlRLPbQ4rPwiiJI+ha1JDErdWEH80EfnHx6+EXF6uO45UQLZGboFvua1bn6iHWZLEo4zV2VdElbjSzDwuUN4qyhbTkRPzAyjNokiVHYFWkcXlxWtuaKtoHeQ+ioQmUb6rdumqc8NluyMQJaJFbE6TIbJ8qIspV7KL5qdWxy2uErxxHusGhOs14C2LNcxbPAed6CdoGL2h0O2MfQlLPPsd1eOxHeqBeC0r6OQQGGg2VHkpp0XThRY3KU6D1xAKnFRAHKKcHjNQ3eHqcRQXDZZltlpOyUEiTIqs+gsaBzJkhEiq1z5hXYUBxtMHg/Cu0VXlGu9yZzWEavO8U08FkTVSVeZ2802sMTRXgXTPfgYE+rbINvc1xXugdqYgUySBBwmePCJqZuEUTy0WygaqHbsKKOgdzGy2f8jXNj6xkMfONxZTDoTlVHHg6R/XbZfrvNsqAqLzEeg7MwEvGQQwzzzd2eyZuXW/Nf+3SdyptlUE0bRrERbNLWz4zhUnEXiKrw5ItIzIvBeMF5PQ4sBhpw2qc3tif0VYZJpM0np4YTEetG9jC4BdFCLXETDuMtynNwB1HohzAYr3oUgCymGlpQhcJUxa/lq8yiPoDVs3igjzmSUx+bjlA0EINgrjCYQVoihxTUMya5sKMCs7MGElmcsnGmDlOgmkEw1PJIGloHGBZEUX7tof0DOUBNtXSo+Rx0MWja4hSkv62hPdlYKN5QwH3KXURmWnMQ/cTvbQ5x8qZXlV2TOMcI1YZWmSd8wkQ2Y2dRoneknhDLjdJW4ga6aXBg4IDrIY3JZ5crRHPQa1VOalVIBXQVS5Izhz36dj1SQNYpm0IJ4lYDj0krltibkLjIJfmSnZHG44MKR184aWTI02Yo4MOm06hNxroZRRFriMUZJAX4wNx4uNEPxPUkedS5IY4KhSLfbCmxEHYSRzmcfX0TppeE+bohGWfTlMvNNRBE4USRx94aXxOjTjaRfZTcs5v/KIHN3R5TNK0lnSG5olxJHy1GSxMwTmDzepQxGWDp+OsxHC62VECR54GlahsPzCTJzgOzSbkjI6Dyte6LDBx34rmMWZRedlo0EYr5shsnWO2iGhOVi9mSgwBRvF7HRzx04Mj9Kl+oN7huKnZfyM6+3+ITn0u+FpH7NUQoz5RRrhidMllC8zShB9pz6Ts5VPRpYPIjGWqHOtIuVoo+F3Wzi9QA0WW3ypni7bZcZa+J49uEF6C7s2JLbhT5VHVQC6iLTBdvoS5KwvaVcnEDlULegcSfH4CpFhiZwY4kho0DcfA3IQ/t8nz6WZzTRm6+ULQpJNDXnQjB1F1fpXVA1B6OIo89CjSXp8jIfVkkE5JWMnW0jj1pO5ASQv9JtWlUB2FNxCKvEGhUW2N6vQbNTcwoQMlzQ6UlG9IMUkdCEnGIGlMZNN1ajx3De46KrdfVL5BILYvfmouJLiMdYvhx0BIPJO3YCqb2g8bar8o6EPFne2F2BkIsfs/o87rF4nzryFhgKv9rzMQOrtf5isLlOFun+Tm1kH1Z4NL7NpNSjfZFZvuYnt/LlbojTH7GU7PCJ4rrer7MfyJml3qviTWIluT85cHLidLCJByP1mvah+nXUwMZxvOj0XzHfDAv/RN7N6BQBeRcnNAEzBHYwi880cfK4Qkh8eferbHXshKT8iK77fGN3o405kfmwGYdqmHXT2WNlxT0jS6CSTpU1Esj8hSS0nniVn84jAtfqfAq4dp9W6MInfVhMLJjjgyddCcxAmYI4mX8c42MR1JO0OJrLiJO0vMahNnJram8BIhEl13N2NG9KkHcgIpVX8/gnIc53lKsZJJtS9Pgrn9JQk0AyeJO+Sagm7Eds2tWNF+WAQpHU7KumLqiQwPLbErJ5YYGg1hIS1mHTrFDqp+O0ABEH0SBqY3nmGSmS5Oov20nKmZN1hFshjUYM6tRQwtKTB0ZP0WkYNbgOGqx3ZltNLS7U1sKuIUt1JAf4Q8VH7LjtCsESquaRM4I+ztw2xil4m0NaCNddLX2uxZAfGIQPN98EXbRpYM0WsCmbl+tie258YIUZHn7QyOieHUYjj1GA7MdDT0pH2kMAjUfMCpsaJPKCkmRqXpd3CSzZbMVwv4Cqnd2Ty1Mo/Gr5BjBEXzmaStgpc23rXhjadvcrU3JfPC0Rde7YXLFwhxNAQKyGpwgCit5ydP3PjEjX43/OqpVhgtWfKHR37kMlQ9DCW6anLBFSves7ormi2DIwme1h1MBv9iPUuYbOJChBkgu855zHS+1GNbgilZ53xyaGHAKCcHz7AjI9/TE5Lie7jme1B99ROSVuMJSXl6gVGSD3Dn9Idov6bZNng88VQovcYzjI6oPBZIM4h9MeRONcCztDF7tQdH2qkHnCKhscsIYkykh9qkgtHRpgfhkgXRdqz5L0OMGy6U9OsWdXpTW+0guKUHZo3HFvKovFrAtDGWlzoYZ3pvNgYPOiI55kDSogg9vZix6GkmRtWzVIHuFTyy0Kk49Kxe6OxHorn4c+lE/HloIcivUrKOr/K6cR0kEMh7ioRQ8ZzBcUY3jeSVI0TVznk7tU1DTSgrBsvgF2XSZPq0va5a6EjPTf/yhY6+sQyt0qAXOkzBR0O4sZxBOh2+UCaIlrZ0MeRgkOJYw2zmMfniNGgFg7BZliLVU1nrbNtDRRl9Hp0Me3iZKZPtiSvfNULLqChfYimqNGS+1b0LjYH3tMBietyZiGWPuVI6K1Uuqghq3kEp5UBvrlOXuVRQ63Sx7kOLE9U8CegdZJTOmXQVrEptYjQ0CI3Bm7btL2ic1NdXe6TFXFbJ1ZdjfFRU0cfyWzoFFBcEtM5Da4h7QB/U0JjG4Hfp6xaoaj25kkUWAKzIsnB5MTeqXxPmJDJ+n99gdcWZT4xu1aLfcBDjMZcPjutCnqd1S6Lax1ZAQYaJGpa55Kq5+zu4IWe3cgPtdPT/rVj6tFZEQeyYfzwUrybIETwyG8jJ6RG91wLc+1lzVyWPlnPPMB6TYnBUKcChNWM1Kc6rNFdB1dk5VJXeSuHQaa14VRTs/pO6KrQbPGcC56h8UdQDvSMqRWxEV5M1W3VVZAlbTX5yBDKAdLd0VnhZB084lpLmk3l3Bzt5lC9L6YpbkJN5n+F7lp/vVRDzkIbX9wImnwYrnSQwnxJIvT8rOdn8iwWIQfOKK5KuCmX7xhdRd9AUd4JoxZ0lblaI4oYAcAmLKOa9y/cuHH1va96dJitaiHjJ0/cCK7fcPejiTDqf6Xw4ms4nXm0u+t7jew+OvmeNUNNj8Hpy+96v8kDLrqnzk8jx0p1rx7x3+N6Bo+9duYhKzszHmEYbizBp/bz5ZQK39T9tR5U8n+KK2yM9Z+iIntwX1+er6aFN5wB1DlPnRC4PmN6S4Y+XXeEZ7ibjXmXc0417mDrT1YnVEX10KwQXK/ZSECjGw1CJ7r0VV+UyiF2Vy2a+m17J0MEyUORyEKrdg5Ax7ACeuIX4ikC2xHkVyDD2761IQU+E2r1SUnEcjHoMjTgizZDOpHCyIb2J19jzSS9PK1DSVKwyImmBZpmByg1cSuUsG5coZ6DET+XMJ1LlC/UjijRWpJ2Cw4ei0DUClIU8MYEyTlHzSS1MmjWBC+pClYx8LYPTe8U/LDArV3NtVOO5wItydrCAnY4azvGjVfg7cWee69OPQEY7Juu5DOAOvu6s2wzUEZyLHYj4fTan7h/BqmSVPaPHdPDRTtC7u4Kh4jSY81liztduLPdiS7EQB4YikiDrh2gSmntAIx2fq6YOemKGOpzJOO2Nz5Z5TucqszUVGokHL2cbYgycDKd9HZK5cu+1HtnNG3MfEp2vHUhMLB5EFGfbhpkGUcXsN1zeyYvZeyJDBKLxgCTHNzkdF2dmCbaMYnNJjNDpaAxdJzl4/6gYunaYsZ9IkJcgC2JNpucqPOYMetZYtHwkm2fKJcZyBoO7zvCh7k9DqKbTo5IZUi708myFGwRaiUK3+VRWgY7HJqVmejY9hFk6atuAjPCIV1jWhk7q5gWJjXJ6Wk4OAyeEfjoaZWQsOaFdTnXIDGEyj22qMu9sejigzyNjLtVgPnE7lk9q+MPZwVwcwGYIB2wJ8RAC+1rRI1BFR0dKptSRLfuEntKl/EfpWMgsy8qWZQPDCPbrCOcWu/AA4RmExwjPojKYRRcpyk4WnQGJKBfJGV4M4nLVPYkqwLgosXK89o8pc0kpmsFEE2hlArlkKO9DPLGbwmTVjsQw7zEYd3LHT3bM47hKTh73Mpgc4BpHXJOIyW7EtFuqfxaJMrsFloRF+/sNoswCNnxMLfbU+kRORiHAbhDvmvnNl8wi0Jj7suGC4lovgbjaiiv6Br9lBsLtY7xrm3i1835GO2JUIXR6gvNydolzlelV2vr0qzMlzmdsLHdc2xE0q9EPuUQHN8+Aj2xsKIMzySYbKoGc5jupwgtB8WQzGHWXharCS5RKJ/G5o2HLuuROOfNmqJ1RZ4uSrch4TAqSYZ7kAL/cIq9H9/00Fq8jBXo2IdNGrIYDU9nElf1CLlPpvAZORaRYKFpl6fRYd8g4a424yrCjSWAvnEpQtjmJFiNvp/GMgt3rDhBUxTeSe3AFCS3j023ZdrzKgF69YQAs4qDAZ140kO2XfEM6ZzGzZfPAAZjz+R5LkhGyI6E6o+u4NE0+PQatvUSq/7AxtCMdu0LZy048vIcwq7F6JjoI/UUuAE0IzjYEt4fZgNOdTg2jgoMWrvyGnLmvS9X6epFVISWj91JRco04qpD3PNaCLoADLTZcClWqWwVfqPjIYZ6GS1coZfYYDtnNGxvBcLZbiiAbufnqbUlYI9sICCtKGJaIUZLnUIQa5dXuFDkOSJhbVVzwY2Yb8OShlJlf5bNd5XRNrCJLFJDYpdowjnqQZdBLMO3Oxsr1A4p4J/ndrvaYuNmGzZXhjoQnf5d20IR2oANj2hYyZJxpDEs/oTFo/SEeCQK6ej8w3EhP3U2TT+b5U+aI+BqbZtjsrplcO5wMPpkxBuHSMZhd0ly0f+BhTAhmk06vYVRytYs360ivapZtiKtufZXKum/cJg3mYn3LLBuw+Q0rw0xvpYQ0CmLvQmupbecqo1wxmEZufYw8lFEmu4127dZkp76QdpCETn3prCGMiFwbybcT/eoGFwayjvbJtwYqLSOOGaG3ktXiIzlt2pAQz4pJL/u1jdFutRaubTJdjjvCw4qX3iMPoenLUGTN10J8cKS5k/khVLFYY66ghyHfTT2rzzeeDziGmxHEBC2VIEuPaLHi2aVWLKNMtNrkMEpwjkv1kdVw9MzCWRSaaCbCR5yxyqE35X0UcrzIjsztQMKfHE91aQk5HibI03zGNIukB+iE0GEXoBNJbldz+nkg30jL530cyvZ+iaC2Sk/A5dITcILBS6NX8tpfZrlDxNVi1pyAE3tDcwIOVOgJOFZjQ8eORQ0tRokSqxaoG7vSfl5aD3C4KL8vGzzDTT4QDPhek8m2o8fbDP6+yP2qmHgmXIwwixryANFWAadcQx/QsjA4lxpKV88jsGis9DBt5TLB7+CxJbj1Ec/WzpGdBqMmthGVWKWAwm+YpN/NVLYYfoKQYdSb9hgOJc17A1VwNy9rVwrWkYJ+KiUWdWB6irEmFUZYbAqLiogtImKnIuI2iIgrm+gqIq4ep0hFRG+2fhZEW/MoJy71zPOkM4z2FLEBK9NIaFYBuFpT6V/rQBvVL82pGGWSofNZQcpTasPLXsolQ4fN0YpRVP0Xg4JUpjgKpzKFsYyxSgG8ZxdFVwZFNTr9gs7i9YseHoVcLWHldLkVmNzKDG2eYdR1keZA3Twp4jKDOmGmA6WbikwM/DGMmeZ3C5nJIxIItulwJsS5hjiLeSpKjay0KpPmsALttNDOLOWSeUZdMQW+RsEKhYT0ERv7lIqqgg1RFZvXvKe52t20ZgtO+TdyAHrkgXA1SDK5uLVcOLZ30zQp5Au9P2i/tqrk+zlV86f5JpaRgMYQs4GSfDzfxE65px+z6JYDdcEUS1FG7docRvBnWLNtmW0WPsYhUqv3pnSfNDgh1elb1Omndu/6D3mUeMmNTXkQbaGmx+2e+IkxZXXguvMhDk4CuRS7LxxSc4ZTgeOEDpyEu0ssCdEnbnClbbuSQePO1WIImpLFcRrRNbBiAqlEc/lFKtFpRLUdLLdKPuOYXN/9XN0dk7mgDMocuWWOEPuzDRqHaBS92feSsrlBm9mjij7be4eK21PTzE4KqmEUVzddyai55lAQDrI1htkSVugVZumqZxL2g+n0BNPSb1xxskMJxFP0eXniZ1tE6nJKZUSJkxuyOIO05bMFwdfBSl1Lt4Kfuttt0A1TxSPVf5zqOlQLO8o3r1NXaHjjwTEXS9OC2swExOB2JHu6Yem2IJIpEmTFj5KQUS96dpFrtoW2fjPDNx9Oo6yjKcPT8AExM3q6YgDv6LFPXTbIqvKqnK5GXoZboGjDDcxaw2OGTIvIoae9TRa5rbGq5jYwUMOhLINHjl4ZTv09+LhMYvujdpDFp+Zc9UCz2uSYX65KEiBC84gmtYmMM9IjAmV6jtBGh0oJQtbpcsYnYh59Vp1QhmIrB5sxlWFJrgu0RGfnsaYwpciYIwChnU5UK2x7RS2M2QILdWISCAEVVz46TT0FPVU861QxJ0XWYpKRsragMw/m9mUY2HNpneU9u92YMfRUXE4tLsC9ri5/saGKIN3etBhGJ3EYqDuhNgPpIJOeJOJWZdkVNmg1JtiX5bBjqGgCFWyKNSO4EHlTi9X7ynA/9tNvE5ndPg+F1Yi97dAXyrmhTPAXDpXaW4hajRdpW/TLj7c/h0aV66L0RbQRE6oi4MwBGRsUGpI2IR9ks0QyO3N0UCPP5ldM05JcTLNzGLkEYshJv85JJ+WkU+OkU+OkU+ekU+OkU+MkdC+2yDoWnjqJr7gqTirXOoDyTgEplitzWo7YHFHV+plsq3003UODoY9hVzoyFotkUepUqaiDbGPpXJaOsYgLYsr0qvI5I7CHLUjXXvKZW4+Jmf+MSjBPrONyYDDvwmVHE8p+XJYqQ0bFghxHm8wZpkqEpBH6UNlfiH10oDzSiyc1RBysWjGvKw74gSgbp5UjQZfDl2TaIGkhXsROWyXANhKaLQDdCG8fG0SAv5UXkhHgH0I6ZpW3gPMg2ttwuFjrtpHyjvIIvaDPnOak2bA3W7QyLb4aZajlJEiTS/S6+e3jsg3vgpPwxLOBg3Xx5/TRRzWhvy74cSF5pFBNzX6aMr58zy9DoSvGAUaZQGua38nQNRp/4qCTadL7lap9Tt1KgmEzQr/xgO3AsYfU1qxptDQZBLHaWwfSSJPXO43S1pi8ivQD5TgwgX2zc3tnl5PsKiKulZF4p768YmRt8UjQzLbKlnQJ+HjLqHgkFqR8qCIprn4YBf+Ws9r6UNlvFTj9Fle51G/6QZF87WsoL2pApW+h55hKsayxhnO+WtJNgbwISaEmHm1N1FQ6yqtmlJyo9UpTpioYi6CAFqVU8XBxVuqqk4BQbx6i1c3daqjk5vhPQrUKqDIfQ+V8Aipb7DT6R7UqyQJPdy88dh881HoRSx7/eOTqc3J+Ms7IdJEvO+SlHTdHYlA1Qnce+AYRarxhlO1R3Tn2tjl2tA3fw8jTyfeqmbB+f5bH5EyM8TZvvtQBdJ+AyeuDKUXh9EuBBjq9Ah0GefXMMr0z0w9naGa5PpkRnXIpoGQxUv3aziBuCa4TQwWbMlbkoX9jyC3TpKQVw8ambBlj0Xt2GRvh5DQiJ7HZiEmGYYwuIlKUTkY+yKK7TkYgiEsFpNyMhBzKEDlC5GFBWQZxnUchA4fHfFStGmcQBB97b/V5eiNkAB0a8q2fhHLSjTQ3/Tiv3v0KPOZ6V7ljQc0UtxXjvUl8lG0UdSZJ6JQ0dErQ8BHzYJKQcRImqroA4VO3POkSCAN2bO2DcNtYm8XWfsH+mFun+5XQW+3vLVAktAdCNIwFxyMaE6WYHLHRmiuhCbmHtHOJ2U9AxUrXzgGF6Iiz6c3Y8uEDrWA/tWEisyneLpx92jDc6tiB6Jzs6Dlz6ptjX9oVLeU9IyO95kC17UZPoid6Cl0W8TnSIYJscYGt1lQ6v6RhBGny4eHSFDNEUQxYcLOqiY4z2nIyorju7pBtT50O6PzZEJPVc48eU9VUm2B1Zn7ogm3p5Dmjk2fdZ9RZs1F+0qTsoaJo/ECuqOTcBlVrugmDMHPkwlDo62Q2r9MdKlJoXlHJzWswG8vND22zJIFU9u23Gz/faukCeed/0agReIzJoq5yzrU1oWs6HPNJPeJwarpX15hm6+CmKmHJvTG+Epw4tfiuxhdjJy89UpFew0BfAyJYRlKC5YwSnchswtrzU4tQ2uTpSGQOTEngTHntpnNs0UOnX42tcBNc1RtcNcghb9Gx8Rp46tle9PVbspvSFZ75lpmfwF1k3FidSN/ehNDal86WywNtpM3t2lbtdm1LOhWjZHCiHdiMCGb6iUSsRjYVE+JJiAfdGsWGopnoq4a7FZ1Aiw5+1K7V8ZnRCtK6wq7K5DOu5eWPw5temWlQY26xl4bl9Au2+tg7D352gsUjWdG29bIY3EKxyyOIWilKK0mro5BPGK2HRymNtqpT2YfC3tQlbgJg6DoQ1UZdiH4BCMoYJTUZNkO4Y/fJUa5AokOlkrGXKGdYIbpNkUmvQAIJvP5Iosq1RJZeS2TVryXiLUnMga0tyfNT7ulFSC5ztST1mAr3wsWTazf5WUrHAtXDoj/FT46KJVHZWnr/kGVocWcwRD8Sg+zJUGVRtoE/uZTjZJAYkiq7mQbI+AYkQaa5wkQ/tWzcJOobgt1cfh3OylhZK2flrQJaXWiVrGarxRpqtVmjrNHWeGtHa6r1Geuz1hHWSdZZ1rXW/dZLlmO7Nga6XHC5zT8Hfy7+PPz5+LPwZ1uOpeizQB0C6RYYKSeiHHOsM6x7Lds+w7aJx7Mzdg5kRvYwjFAT7On2YdbpFv8Qh3iJM5PJZnO5fL5QCIJiMQy/dcB+HWFheMHa/HlgZm788yz9c4wLGt/LB1/8WLBxcwY8Aw6h8f0AcRDJB/qXC8HIzXj6PGAmgLSUAQngDKXgsiC4zU5ROgZNxjwnR55w+FHWrBkzZ1gTJ8aT4h12iCdNwn/18E+cmE4vQPSJiERA1IkThaRclHfcQjO+wDgo8Pxiy/DyuAnbVNpaw0y2afCI0Z+aOGWXHcaPGTmkNHTLrbbdcdepk7bbetTYT++02+6Tt995j2l9/g7q2BPlKwZ7kHYXkAWUAKMA0wALAJsJNsAFZAElwCjANMACwGYrWRoGF7gcYD4EvA94HfAC4F7AGQCb8BHgA8B7gNcAmwCrAKdv3pz7P/aX///8l/2Ev5Qe/7/5i5oHWblsPlPwA6/ohk6TXbJw8DvocY00+kYis0bY84ACIACEgJKR7BbAUECbkYLRgPGAHQFTAZ8BfBZwBOAkwFmAawH3A14COGgOrzUH+1KiGsHpA24f8PqA3wtw/DyYn6JpLFLWFCU05G9hGudE00DnmEZ6r3Q18ANsB+ACPEAGkAMUARFgGCAGTABMBxyGeUJLcFTfYriEfsjNGMgayBnIGygYCAwUDYQCybrWIOxdzOQtE2TVckguGhz8InfJhOwp285p+vudB+Z/tMXq47MXvFU9cafrj3jl2ouPvGKH855pum75s1/d8pUnNp23Ye0Px+9zW/7u0275Rna75X/8/sh7Fh4Z3T7yjXhxde/uO958Pll59tfXPT38Ly8/d/yBy9a99atzHj/rPy7talm1qPPIU98//MO/PnzUt/c/1G7d0JU54ojdS/94O8x976F47tQvRcft83Jy2BM93cdc89jL7m4d67zZH54Tru1cVrj+H4uW7dR16a17PvnwXc9Mf3/FTS91rd/h5EM3tj8aPvnsF3d/7Oc3Tdiw9a7W8weefuBTDw2Zs+aeZW8tLY9eveQ/fnTxnY+Wrr97+RXLnXG58/wv37qh+OCYV4JbvnnaCds373PsF34+cs69u2939G0Lk2fnZ7qfsVfEay/9VPTEq98655buUctuy1z/8j1X7rhu+Z+ueTj7lUnvW6XzF+UvGXZp0wt3hdUF2+1+fO6HXUd+3z70iD+8dWBn5+Fzukasn3DUOftZh7/xt4szXV+73m67963cmQevLr394IbFRx3wyu2D/rx85XdOO++ODzaOfO7wmds9PfjN0x7/7lH7rPvnF+bcuu/vDly26zxrxbUvTrhrXXL9xllPXbx+9+NXP3bdP9968vHqK8d95oMNcyfNOO+YX6xZftjTJ23n7fX7ke6OX96ncOPDp4XP7dbtH3Bj4oyNomDluXHx4a2WHXvwknNOiPPrjr7jypfn/K7p/ee/dOnDG7apXLpm8dJFT/1q6O5LvnhGuPTTUw+9+/ZfdN25Kj5r+d9uW3LPj4sv3ub+eO0t83bpeOLPvzxl7eWDt37GO2vIsycNCo546eypR160R/X4cNEh1VODR5tevnxz/sKxN1uFW67Ifn2vC0uvrb02981j37C3/Oi+zHH/dfDh7/7mmKNO757ctcUfM51z57et+2jT+Me/dcipT0ePzHrumBM23vH++6+uPP8zK28f+sT5iw/707VPPvmNCx+7evV966cc9MbG/V8/5q7Hjjl4xQ3PZZZNmz351r2fHh+u36et8Mt3ZrmTDzvVS+5/9bCNn994zKJXz5878asrj5vx3SVzfjP8rKNXXLX2hK0mv3jsQRefUnzE7QjuumOIU9l+a//zK6fe+cC44O5bf3DI0vFedcmhP9381H0TH12z7DtXbNh25M3P/+fi+478ydZvHPHXy66tnly48HjnZ5n8ZdMmN/3lnGOyPS0HW/6Zs+65uPXU5S/ePP6Wr01pu634k/PX/iBc+cTvb3/12dPKG58J1q19/Nw9X1z3+ntLnjt27llPj/rtkJVn/OfWd7zzwimLTzil4/Zhfzgkd96J1dLmX0/NHH1oYDdvvuKoC467+fD3Ht/cOaf90a4hX3mxcNXf14ZPfe4sb7/7lri7zNn6mJ++O+SwNft2HDfzmVPm7jGr+tjNzx7y5Iajg43TX5u6fufP3rziZw9ccdfzCx69teOVzcsmjHjj7ru/fd+dv55w4ZLP3nDt0jH+5DV3Xph56rfbHPz85+45ZsPoT5969JLls+asdtqO/a+Lxp/wqZ1XBkuvPr94f9tG/5DvvcpjLLZvy5+lf6Kc9TPZXL4Q8JGzN9fxXN/32Ml9HR3oyOAaG7Esr9kfVJ6YZFoOnJOtdLfucMb3r86N3vOWewcf9NhrY3Y8vOfMfGHI0GFbDN9q67HjtvnUpJ0m7zxll10/s1f7jI69Z8767Oc+/x8Hf+GLhxxx5FFHH3PscdWT5p08/5SvfPXUs84+59xvnvetb//gwosuvuSHl/7ox9dce931N/x00c9uvHXJ0mW33b78jjvvu3/1Aw/+6qGHf7328XVPPPnU+g1Pv/DiSy///g9//NMrf379jTffevudd997/x9WcuaoYHvXTv8+qXSO57leFqXDu1Os5N5RwdUsXbPnlydmBiUHZud0t1R2OCPXuuf3r77l3vzoHQ96bfDhPY8VhoyZ9MJWr7N8Urx/rXRn/q+KVytdQ+HeiIPtHM6a5Z8H8AEZQBOetsTTHnj6BoaOjzBsfGTnkWQcknCqzX8DRv3ILVgfZQOMvxOC8QUZljQh8wnwOxyJvoIE7yHBO4h85ubIOguQLJ0YjC0iQhsifBUR3gG2d4DpbLw8B3Au4JuA8xj5LUQegYjvA8u7wPJWqWh9Cy++DfgO4LuA7wHOF8w7BGN3BZ3vAuPbiHgBAhcCvg/4AeBCwEWAiwUzIk9F5PeA9RIE/BBwKeBHgB8DfgK4DHC5YN4xGHsqaH0XdF6BgCsBVwGuBlwDuBZwHeB6wYzIX2PBQMINCPgpYBHgZ4AbAT8H3AT4hWCeFIz9BwuHyDcj4JeAxYBbALcClgCWApYJZkR+D4VbtaBk3YaA2wHLAXcA7gTcBVgBuFsw7xSMfR+RV+LhHsAqwL2A+wD3A1YDHgA8KJh3AhmI/Cs8PAR4GPBrwG8AjwB+C/gd4FHBPBmYwbY1eHgMsBbwOGAd4AnAk4CnAOsF82RgRuQNeHga8AzgWcBzgOcBGwGbAC8I5p1RQPD4RTy8BHgZ8HvAHwB/BPwJ8Argz4IZkf8Cz18BfwP8HfAq4DXA64A3AG8C3mLkxVOC8e8D89t4eAfwLuA9wPuAfwD+CfgA8CHgIyZ4Y0qwTV2iKeHDIIdbAKZYGZHstyi4KpOgfJdgLMV+F7x8B/X5tko15ZCSXBdaoRyR2T7eVakWSbkPddoozUZ4gXnXYCzaChsShZWS0q9EXyKYd5UmwEY1oERfIZinahNAXQ4o0TcIZkT+qjaBASX6ZsG8m7YX1OWAEn2bYEZktO8BpVmFmJh3D8aifQ8ozSrExLy7tJcBpVmFmJj3kPYyoDSrEBPzHtJeBpRmFWJingbM2WBAaVZBJmZE3jSt6b+V6LdFqldZwXi2rvshPf+SWL+GFOf6kVkRFAD0DwOMMwuv3QFFA01mkTYcsCtgN8DOJjzsk3YXQDtginnfDBgCGGnwHgw4ALAHYJB5t5WJvxfgeMAxgH0NztEmr+mAAwHHAroBhwImmvD9iNek+yrgK4CjAHubeF8GHA44EXCkiXeEoW2QyWd7hvUBvhtsyr1nP++HmXdT+NwXFKem+z8Kb3o7Tb+n9WvTv3xW1/QJz3xuOp+f3qVp+uBX75923wMd0yzbzzSV2kY4XjaMho/8lJsrNm+x5Tbj88GgYaPGbbtToWVoPHa7SZNbh5S3/vSOO+82uLLV9jtMmbr76DETJu6y6x7TNq/fvMmKqIaShbBtUaXzjHW2tflpq+iiU212m61p/w9F"

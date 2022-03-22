package kuick.samples.rpc.users

import kuick.repositories.jasync.JasyncPool
import kuick.repositories.jasync.ModelRepositoryJasync

class UsersRepository(pool: JasyncPool): ModelRepositoryJasync<String, User>(pool, User::class, "users", User::userId)

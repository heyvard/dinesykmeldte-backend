package no.nav.syfo.minesykmeldte.db

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.hendelser.db.HendelseDbModel
import no.nav.syfo.narmesteleder.db.NarmestelederDb
import no.nav.syfo.objectMapper
import no.nav.syfo.soknad.db.SoknadDbModel
import no.nav.syfo.soknad.toSoknadDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.testutils.getFileAsString
import no.nav.syfo.util.TestDb
import no.nav.syfo.util.createSoknadDbModel
import no.nav.syfo.util.createSykmeldingDbModel
import no.nav.syfo.util.createSykmeldtDbModel
import no.nav.syfo.util.insertHendelse
import no.nav.syfo.util.insertOrUpdate
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

class MineSykmeldteDbTest : Spek({
    val narmestelederDb = NarmestelederDb(TestDb.database)
    val minesykmeldteDb = MineSykmeldteDb(TestDb.database)

    afterEachTest {
        TestDb.clearAllData()
    }

    describe("Test getting sykmeldte from database") {
        it("Should not get any") {
            val sykmeldte = minesykmeldteDb.getMineSykmeldte("1")
            sykmeldte.size shouldBeEqualTo 0
        }
        it("Should get sykmeldte without soknad") {
            narmestelederDb.insertOrUpdate(
                id = UUID.randomUUID().toString(),
                orgnummer = "orgnummer",
                fnr = "12345678910",
                narmesteLederFnr = "01987654321"
            )
            TestDb.database.insertOrUpdate(
                createSykmeldingDbModel(
                    sykmeldingId = "0615720a-b1a0-47e6-885c-8d927c35ef4c",
                ),
                createSykmeldtDbModel()
            )

            val sykmeldtDbModel = minesykmeldteDb.getMineSykmeldte("01987654321")
            sykmeldtDbModel.size shouldBeEqualTo 1
            sykmeldtDbModel[0].sykmelding shouldNotBeEqualTo null
            sykmeldtDbModel[0].soknad shouldBeEqualTo null
        }
        it("should get sykmeldt with soknad") {
            narmestelederDb.insertOrUpdate(
                id = UUID.randomUUID().toString(),
                orgnummer = "orgnummer",
                fnr = "12345678910",
                narmesteLederFnr = "01987654321"
            )
            val sykmeldingId = UUID.randomUUID().toString()
            TestDb.database.insertOrUpdate(createSykmeldingDbModel(sykmeldingId), createSykmeldtDbModel())

            TestDb.database.insertOrUpdate(getSoknad(sykmeldingId = sykmeldingId))

            val sykmeldtDbModel = minesykmeldteDb.getMineSykmeldte("01987654321")
            sykmeldtDbModel.size shouldBeEqualTo 1
            sykmeldtDbModel[0].sykmelding shouldNotBeEqualTo null
            sykmeldtDbModel[0].soknad shouldNotBeEqualTo null
        }

        it("Should get sykmeldt with 5 sykmelding and 4 soknad") {
            narmestelederDb.insertOrUpdate(
                id = UUID.randomUUID().toString(),
                orgnummer = "orgnummer",
                fnr = "12345678910",
                narmesteLederFnr = "01987654321"
            )

            repeat(5) {
                val sykmeldingId = UUID.randomUUID().toString()
                TestDb.database.insertOrUpdate(createSykmeldingDbModel(sykmeldingId), createSykmeldtDbModel())

                TestDb.database.insertOrUpdate(getSoknad(sykmeldingId = sykmeldingId))
            }
            val sykmeldingId = UUID.randomUUID().toString()
            TestDb.database.insertOrUpdate(createSykmeldingDbModel(sykmeldingId), createSykmeldtDbModel())

            val sykmeldtDbModel = minesykmeldteDb.getMineSykmeldte("01987654321")
            sykmeldtDbModel.size shouldBeEqualTo 6

            sykmeldtDbModel.filter { it.soknad == null }.size shouldBeEqualTo 1
        }
        it("Should get sykmelding") {
            narmestelederDb.insertOrUpdate(
                id = UUID.randomUUID().toString(),
                orgnummer = "orgnummer",
                fnr = "12345678910",
                narmesteLederFnr = "01987654321"
            )
            val sykmeldingId = UUID.randomUUID().toString()
            TestDb.database.insertOrUpdate(createSykmeldingDbModel(sykmeldingId), createSykmeldtDbModel())

            val sykmelding =
                minesykmeldteDb.getSykmelding(sykmeldingId = sykmeldingId, "01987654321")

            sykmelding shouldNotBeEqualTo null
        }
    }

    describe("Marking sykmeldinger as read") {
        it("should mark as read when sykmelding belongs to leders ansatt") {
            val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
            val sykmelding = createSykmeldingDbModel("sykmelding-id-1", pasientFnr = "pasient-1", orgnummer = "kul-org")
            narmestelederDb.insertOrUpdate(
                id = UUID.randomUUID().toString(),
                fnr = "pasient-1",
                orgnummer = "kul-org",
                narmesteLederFnr = "leder-fnr-1"
            )
            TestDb.database.insertOrUpdate(sykmelding, sykmeldt)

            val didMarkAsRead = minesykmeldteDb.markSykmeldingRead("sykmelding-id-1", "leder-fnr-1")
            val oppdatertSykmelding = minesykmeldteDb.getSykmelding("sykmelding-id-1", "leder-fnr-1")?.second

            didMarkAsRead.`should be true`()
            oppdatertSykmelding?.lest shouldBeEqualTo true
        }

        it("should not mark as read when sykmelding does not belong to leders ansatt") {
            val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
            val sykmelding = createSykmeldingDbModel("sykmelding-id-1", pasientFnr = "pasient-1", orgnummer = "kul-org")
            narmestelederDb.insertOrUpdate(
                UUID.randomUUID().toString(),
                fnr = "pasient-2",
                orgnummer = "kul-org",
                narmesteLederFnr = "leder-fnr-1"
            )
            TestDb.database.insertOrUpdate(sykmelding, sykmeldt)
            val didMarkAsRead = minesykmeldteDb.markSykmeldingRead("sykmelding-id-1", "leder-fnr-1")

            didMarkAsRead.`should be false`()
        }
    }

    describe("Marking søknader as read") {
        it("should mark as read when søknad belongs to leders ansatt") {
            val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
            val sykmelding = createSykmeldingDbModel("sykmelding-id-1", pasientFnr = "pasient-1", orgnummer = "kul-org")
            val soknad = createSoknadDbModel(
                "soknad-id-1",
                sykmeldingId = "sykmelding-id-1",
                pasientFnr = "pasient-1",
                orgnummer = "kul-org"
            )
            narmestelederDb.insertOrUpdate(
                UUID.randomUUID().toString(),
                fnr = "pasient-1",
                orgnummer = "kul-org",
                narmesteLederFnr = "leder-fnr-1"
            )
            TestDb.database.insertOrUpdate(sykmelding, sykmeldt)
            TestDb.database.insertOrUpdate(soknad)
            val didMarkAsRead = minesykmeldteDb.markSoknadRead("soknad-id-1", "leder-fnr-1")
            val oppdatertSoknad = minesykmeldteDb.getSoknad("soknad-id-1", "leder-fnr-1")?.second

            didMarkAsRead.`should be true`()
            oppdatertSoknad?.lest shouldBeEqualTo true
        }

        it("should not mark as read when søknad does not belong to leders ansatt") {
            val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
            val sykmelding = createSykmeldingDbModel("sykmelding-id-1", pasientFnr = "pasient-1", orgnummer = "kul-org")
            val soknad = createSoknadDbModel(
                "soknad-id-1",
                sykmeldingId = "sykmelding-id-1",
                pasientFnr = "pasient-1",
                orgnummer = "kul-org"
            )
            narmestelederDb.insertOrUpdate(
                UUID.randomUUID().toString(),
                fnr = "pasient-2",
                orgnummer = "kul-org",
                narmesteLederFnr = "leder-fnr-1"
            )
            TestDb.database.insertOrUpdate(sykmelding, sykmeldt)
            TestDb.database.insertOrUpdate(soknad)
            val didMarkAsRead = minesykmeldteDb.markSoknadRead("soknad-id-1", "leder-fnr-1")

            didMarkAsRead.`should be false`()
        }
    }

    describe("Markere hendelser som lest") {
        it("Skal markere hendelsen som lest hvis den tilhører lederens ansatt") {
            val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
            val sykmelding = createSykmeldingDbModel("sykmelding-id-1", pasientFnr = "pasient-1", orgnummer = "kul-org")
            narmestelederDb.insertOrUpdate(
                UUID.randomUUID().toString(),
                fnr = "pasient-1",
                orgnummer = "kul-org",
                narmesteLederFnr = "leder-fnr-1"
            )
            val hendelse = HendelseDbModel(
                id = "hendelse-id-1",
                pasientFnr = "pasient-1",
                orgnummer = "kul-org",
                oppgavetype = "OPPGAVETYPE",
                lenke = "https://link",
                tekst = "tekst",
                timestamp = OffsetDateTime.now(),
                utlopstidspunkt = null,
                ferdigstilt = false,
                ferdigstiltTimestamp = null
            )
            TestDb.database.insertOrUpdate(sykmelding, sykmeldt)
            TestDb.database.insertHendelse(hendelse)
            val didMarkAsRead = minesykmeldteDb.markHendelseRead("hendelse-id-1", "leder-fnr-1")
            val hendelseErFerdigstilt = minesykmeldteDb.getHendelser("leder-fnr-1").isEmpty()

            didMarkAsRead.`should be true`()
            hendelseErFerdigstilt shouldBeEqualTo true
        }

        it("Skal ikke markere hendelsen som lest hvis den ikke tilhører lederens ansatt") {
            val sykmeldt = createSykmeldtDbModel(pasientFnr = "pasient-1")
            val sykmelding = createSykmeldingDbModel("sykmelding-id-1", pasientFnr = "pasient-1", orgnummer = "kul-org")
            narmestelederDb.insertOrUpdate(
                UUID.randomUUID().toString(),
                fnr = "pasient-2",
                orgnummer = "kul-org",
                narmesteLederFnr = "leder-fnr-1"
            )
            TestDb.database.insertHendelse(
                HendelseDbModel(
                    id = "hendelse-id-0",
                    pasientFnr = "pasient-1",
                    orgnummer = "kul-org",
                    oppgavetype = "OPPGAVETYPE",
                    lenke = null,
                    tekst = null,
                    timestamp = OffsetDateTime.now(),
                    utlopstidspunkt = null,
                    ferdigstilt = false,
                    ferdigstiltTimestamp = null
                )
            )
            TestDb.database.insertOrUpdate(sykmelding, sykmeldt)

            val didMarkAsRead = minesykmeldteDb.markHendelseRead("hendelse-id-0", "leder-fnr-1")

            didMarkAsRead.`should be false`()
        }
    }
})

fun getSoknad(
    sykmeldingId: String = UUID.randomUUID().toString(),
    soknadId: String = UUID.randomUUID().toString(),
): SoknadDbModel {
    return createSykepengesoknadDto(soknadId, sykmeldingId).toSoknadDbModel()
}

fun createSykepengesoknadDto(
    soknadId: String,
    sykmeldingId: String,
) = objectMapper.readValue<SykepengesoknadDTO>(
    getFileAsString("src/test/resources/soknad.json")
).copy(
    id = soknadId,
    fom = LocalDate.now().minusMonths(1),
    tom = LocalDate.now().minusWeeks(2),
    sendtArbeidsgiver = LocalDateTime.now().minusWeeks(1),
    sykmeldingId = sykmeldingId
)

fun getSykmeldt(latestTom: LocalDate = LocalDate.now()): SykmeldtDbModel {
    return SykmeldtDbModel(
        "12345678910",
        "Navn",
        LocalDate.now(),
        latestTom
    )
}

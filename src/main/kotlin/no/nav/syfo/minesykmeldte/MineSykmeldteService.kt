package no.nav.syfo.minesykmeldte

import no.nav.syfo.minesykmeldte.MineSykmeldteMapper.Companion.toPreviewSoknad
import no.nav.syfo.minesykmeldte.MineSykmeldteMapper.Companion.toPreviewSykmelding
import no.nav.syfo.minesykmeldte.db.MinSykmeldtDbModel
import no.nav.syfo.minesykmeldte.db.MineSykmeldteDb
import no.nav.syfo.minesykmeldte.model.AktivitetIkkeMulig
import no.nav.syfo.minesykmeldte.model.Arbeidsgiver
import no.nav.syfo.minesykmeldte.model.ArbeidsrelatertArsak
import no.nav.syfo.minesykmeldte.model.ArbeidsrelatertArsakEnum
import no.nav.syfo.minesykmeldte.model.Avventende
import no.nav.syfo.minesykmeldte.model.Behandler
import no.nav.syfo.minesykmeldte.model.Behandlingsdager
import no.nav.syfo.minesykmeldte.model.Gradert
import no.nav.syfo.minesykmeldte.model.MinSykmeldtKey
import no.nav.syfo.minesykmeldte.model.Periode
import no.nav.syfo.minesykmeldte.model.PreviewSykmeldt
import no.nav.syfo.minesykmeldte.model.Reisetilskudd
import no.nav.syfo.minesykmeldte.model.Sykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.BehandlerAGDTO
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import no.nav.syfo.sykmelding.db.SykmeldingDbModel
import no.nav.syfo.sykmelding.db.SykmeldtDbModel
import no.nav.syfo.util.toFormattedNameString
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class MineSykmeldteService(
    private val mineSykmeldteDb: MineSykmeldteDb,
) {
    fun getMineSykmeldte(lederFnr: String): List<PreviewSykmeldt> =
        mineSykmeldteDb.getMineSykmeldte(lederFnr).groupBy { it.toMinSykmeldtKey() }.map { it ->
            PreviewSykmeldt(
                narmestelederId = it.key.narmestelederId,
                orgnummer = it.key.orgnummer,
                fnr = it.key.fnr,
                navn = it.key.navn,
                startdatoSykefravar = it.key.startDatoSykefravaer,
                friskmeldt = isFriskmeldt(it),
                previewSykmeldinger = it.value.distinctBy { it.sykmeldingId }.map { sykmeldtDbModel ->
                    toPreviewSykmelding(sykmeldtDbModel)
                },
                previewSoknader = it.value.mapNotNull { mapNullableSoknad(it) }
            )
        }

    fun getSykmelding(sykmeldingId: UUID, lederFnr: String): Sykmelding? {
        return mineSykmeldteDb.getSykmelding(sykmeldingId, lederFnr)?.toSykmelding()
    }
}

private fun isFriskmeldt(it: Map.Entry<MinSykmeldtKey, List<MinSykmeldtDbModel>>): Boolean {
    val latestTom: LocalDate = it.value
        .flatMap { it.sykmelding.sykmeldingsperioder }
        .maxOf { it.tom }

    return ChronoUnit.DAYS.between(latestTom, LocalDate.now()) > 16
}

private fun mapNullableSoknad(sykmeldtDbModel: MinSykmeldtDbModel) =
    sykmeldtDbModel.soknad?.let { toPreviewSoknad(it, sykmeldtDbModel.lestSoknad) }

private fun MinSykmeldtDbModel.toMinSykmeldtKey(): MinSykmeldtKey = MinSykmeldtKey(
    narmestelederId = this.narmestelederId,
    orgnummer = this.orgnummer,
    navn = this.sykmeldtNavn,
    fnr = this.sykmeldtFnr,
    startDatoSykefravaer = this.startDatoSykefravar,
)

private fun Pair<SykmeldtDbModel, SykmeldingDbModel>.toSykmelding(): Sykmelding {
    val (sykmeldt, sykmelding) = this

    return Sykmelding(
        sykmeldingId = sykmelding.sykmeldingId,
        kontaktDato = sykmelding.sykmelding.kontaktMedPasient.kontaktDato,
        fnr = sykmelding.pasientFnr,
        lest = sykmelding.lest,
        arbeidsgiver = Arbeidsgiver(
            navn = sykmelding.orgnavn,
            orgnummer = sykmelding.orgnummer,
            yrke = sykmelding.sykmelding.arbeidsgiver.yrkesbetegnelse
        ),
        perioder = sykmelding.sykmelding.sykmeldingsperioder.map { it.toSykmeldingPeriode() },
        arbeidsforEtterPeriode = sykmelding.sykmelding.prognose?.arbeidsforEtterPeriode,
        hensynArbeidsplassen = sykmelding.sykmelding.prognose?.hensynArbeidsplassen,
        tiltakArbeidsplassen = sykmelding.sykmelding.tiltakArbeidsplassen,
        innspillArbeidsplassen = sykmelding.sykmelding.meldingTilArbeidsgiver,
        behandler = sykmelding.sykmelding.behandler.let {
            Behandler(
                navn = it.formatName(),
                hprNummer = it.hpr,
                telefon = it.tlf,
            )
        },
        startdatoSykefravar = sykmeldt.startdatoSykefravaer,
        navn = sykmeldt.pasientNavn,
    )
}

private fun SykmeldingsperiodeAGDTO.toSykmeldingPeriode(): Periode =
    when (this.type) {
        PeriodetypeDTO.AKTIVITET_IKKE_MULIG -> AktivitetIkkeMulig(
            this.fom,
            this.tom,
            this.aktivitetIkkeMulig?.arbeidsrelatertArsak?.let {
                ArbeidsrelatertArsak(
                    beskrivelse = it.beskrivelse,
                    arsak = it.arsak.map { arsak ->
                        ArbeidsrelatertArsakEnum.valueOf(arsak.toString())
                    }
                )
            }
        )
        PeriodetypeDTO.AVVENTENDE -> Avventende(
            this.fom,
            this.tom,
            tilrettelegging = this.innspillTilArbeidsgiver,
        )
        PeriodetypeDTO.BEHANDLINGSDAGER -> Behandlingsdager(
            this.fom,
            this.tom,
        )
        PeriodetypeDTO.GRADERT -> {
            val gradering = this.gradert
            requireNotNull(gradering) {
                "Gradert periode uten gradert-data burde ikke eksistere"
            }

            Gradert(
                this.fom,
                this.tom,
                gradering.grad,
                gradering.reisetilskudd,
            )
        }
        PeriodetypeDTO.REISETILSKUDD -> Reisetilskudd(
            this.fom,
            this.tom,
        )
    }

private fun BehandlerAGDTO.formatName(): String = toFormattedNameString(fornavn, mellomnavn, etternavn)

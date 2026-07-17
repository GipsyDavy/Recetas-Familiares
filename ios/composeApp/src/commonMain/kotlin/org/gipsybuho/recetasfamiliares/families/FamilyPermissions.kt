package org.gipsybuho.recetasfamiliares.families

import org.gipsybuho.recetasfamiliares.network.FamilyDto

private val EDITOR_ROLES = setOf("OWNER", "ADMIN")

/** Familias destino de una copia de receta: rol editor y distintas de la activa
 *  (mismo filtro que Android; el backend lo valida igualmente). */
fun copyTargets(families: List<FamilyDto>, activeFamilyId: String?): List<FamilyDto> =
    families.filter { it.id != activeFamilyId && it.role?.uppercase() in EDITOR_ROLES }

/** Criterio del backend (FamilyService.createFamily): crear familia exige rol
 *  editor en alguna membresía, o no tener ninguna. */
fun canCreateFamily(families: List<FamilyDto>): Boolean =
    families.isEmpty() || families.any { it.role?.uppercase() in EDITOR_ROLES }

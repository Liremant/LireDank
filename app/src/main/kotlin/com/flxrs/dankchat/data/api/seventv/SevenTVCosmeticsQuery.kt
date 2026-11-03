package com.flxrs.dankchat.data.api.seventv

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 7TV cosmetics (paints and badges) are only exposed over the v4 GraphQL endpoint; the v3 REST user payload
 * carries just the cosmetic ids. One query returns both cosmetics, so a sender costs a single request.
 */
internal object SevenTVCosmeticsQuery {
    private val DOCUMENT =
        """
        query Cosmetics(${'$'}platformId: String!) {
          users {
            userByConnection(platform: TWITCH, platformId: ${'$'}platformId) {
              style {
                activePaint {
                  id
                  name
                  data {
                    layers {
                      ty {
                        __typename
                        ... on PaintLayerTypeLinearGradient { angle repeating stops { at color { r g b a } } }
                        ... on PaintLayerTypeRadialGradient { repeating stops { at color { r g b a } } }
                        ... on PaintLayerTypeSingleColor { color { r g b a } }
                        ... on PaintLayerTypeImage { image_url }
                      }
                    }
                    shadows { color { r g b a } offsetX offsetY blur }
                  }
                }
                activeBadge { id name images { url } }
              }
            }
          }
        }
        """.trimIndent()

    fun body(platformId: String): SevenTVCosmeticsRequest = SevenTVCosmeticsRequest(query = DOCUMENT, variables = SevenTVCosmeticsVariables(platformId))
}

@Keep
@Serializable
internal data class SevenTVCosmeticsRequest(
    val query: String,
    val variables: SevenTVCosmeticsVariables,
)

@Keep
@Serializable
internal data class SevenTVCosmeticsVariables(
    val platformId: String,
)

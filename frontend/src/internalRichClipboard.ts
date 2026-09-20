const INTERNAL_RICH_TEXT_MIME = 'application/x-router-command-whitelist-rich-text'
const INTERNAL_RICH_TEXT_MARKER = '<!--router-command-whitelist-rich-text-->'

export const writeInternalRichText = (clipboard: DataTransfer, html: string, text: string) => {
  clipboard.setData('text/plain', text)
  clipboard.setData('text/html', INTERNAL_RICH_TEXT_MARKER + html)
  clipboard.setData(INTERNAL_RICH_TEXT_MIME, html)
}

export const isInternalRichText = (clipboard: DataTransfer) => {
  if (clipboard.getData(INTERNAL_RICH_TEXT_MIME)) return true
  return clipboard.getData('text/html').includes(INTERNAL_RICH_TEXT_MARKER)
}

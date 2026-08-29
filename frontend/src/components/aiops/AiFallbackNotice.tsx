import { Alert, Typography } from '@mui/material';
import { buildFallbackNoticeCopy, FallbackMetadata } from 'helpers/aiFallback';

interface AiFallbackNoticeProps {
  analysis: FallbackMetadata;
}

const AiFallbackNotice = ({ analysis }: AiFallbackNoticeProps) => {
  const notice = buildFallbackNoticeCopy(analysis);
  if (!notice) {
    return null;
  }

  return (
    <Alert severity="warning" sx={{ alignItems: 'flex-start' }}>
      <Typography fontWeight={700}>{notice.title}</Typography>
      <Typography variant="body2">
        {notice.introduction}
        {notice.reason ? ` Reason: ${notice.reason}` : ''}
      </Typography>
      <Typography variant="body2" sx={{ mt: 0.5 }}>
        {notice.engineNote}
      </Typography>
      <Typography variant="caption" display="block" color="text.secondary" sx={{ mt: 0.75 }}>
        Requested provider: {notice.requestedProvider}
        {notice.requestedModel ? ` · Requested model: ${notice.requestedModel}` : ''}
      </Typography>
      {notice.code && (
        <Typography variant="caption" display="block" color="text.secondary">
          Fallback code: {notice.code}
        </Typography>
      )}
    </Alert>
  );
};

export default AiFallbackNotice;
